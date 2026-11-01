package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the Claude Code lock file for NetBeans IDE integration.
 * Creates and maintains the lock file that Claude Code CLI uses to discover
 * and connect to the NetBeans IDE instance.
 */
public class LockFileManager {
    
    private static final Logger LOGGER = Logger.getLogger(LockFileManager.class.getName());
    private static final String CLAUDE_DIR = ".claude";
    private static final String IDE_DIR = "ide";
    private final ObjectMapper objectMapper;
    private Path lockFilePath;
    private boolean lockFileCreated = false;
    
    public LockFileManager() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Creates the lock file with current NetBeans instance information.
     * 
     * @param port The WebSocket server port
     * @param pid The NetBeans process ID
     * @param authToken The token the WebSocket server requires from clients
     */
    public void createLockFile(int port, long pid, String authToken) {
        try {
            // Set the lock file path based on the port
            this.lockFilePath = getLockFilePath(port);

            // Remove lock files left behind by NetBeans instances that crashed
            cleanStaleLockFiles(lockFilePath.getParent());
            
            writeLockFile(lockFilePath, pid, authToken, getWorkspaceFolders());
            lockFileCreated = true;
            
            LOGGER.log(Level.INFO, "Created Claude Code lock file at: {0}", lockFilePath);
            LOGGER.log(Level.INFO, "WebSocket server available on port: {0}", port);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create lock file", e);
            Exceptions.printStackTrace(e);
        }
    }
    
    /**
     * Updates the lock file with new workspace information.
     */
    public void updateLockFile() {
        if (!lockFileCreated || !Files.exists(lockFilePath)) {
            return;
        }
        
        try {
            // Read existing lock file
            ObjectNode lockData = (ObjectNode) objectMapper.readTree(lockFilePath.toFile());
            
            // Update workspace folders
            List<String> workspaceFolders = getWorkspaceFolders();
            lockData.set("workspaceFolders", objectMapper.valueToTree(workspaceFolders));
            
            // Write back to file
            writeSecurely(objectMapper, lockFilePath, lockData);
            
            LOGGER.log(Level.FINE, "Updated lock file with new workspace folders");
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update lock file", e);
        }
    }
    
    /**
     * Deletes NetBeans lock files whose process is no longer running, so
     * Claude Code does not offer connections to dead IDE instances. Lock
     * files of other IDEs and unreadable files are left alone.
     *
     * @param ideDir the directory containing the lock files
     */
    static void cleanStaleLockFiles(Path ideDir) {
        if (!Files.isDirectory(ideDir)) {
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(ideDir, "*.lock")) {
            for (Path file : files) {
                try {
                    com.fasterxml.jackson.databind.JsonNode data = mapper.readTree(file.toFile());
                    if (data == null || !"NetBeans".equals(data.path("ideName").asText())
                            || !data.path("pid").canConvertToLong()) {
                        continue;
                    }
                    long pid = data.get("pid").asLong();
                    boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
                    if (!alive) {
                        Files.deleteIfExists(file);
                        LOGGER.log(Level.INFO, "Removed stale lock file {0} (process {1} is gone)",
                                new Object[]{file, pid});
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Skipping unreadable lock file " + file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan for stale lock files", e);
        }
    }

    /**
     * Writes a lock file containing the given data.
     *
     * @param lockFile path of the lock file
     * @param pid the NetBeans process ID
     * @param authToken the token clients must send to connect
     * @param workspaceFolders the open workspace folders
     * @throws IOException if writing fails
     */
    static void writeLockFile(Path lockFile, long pid, String authToken, List<String> workspaceFolders) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode lockData = mapper.createObjectNode();
        lockData.put("pid", pid);
        lockData.put("ideName", "NetBeans");
        lockData.put("transport", "ws");
        lockData.put("authToken", authToken);
        lockData.set("workspaceFolders", mapper.valueToTree(workspaceFolders));
        writeSecurely(mapper, lockFile, lockData);
    }

    /**
     * Writes JSON to a file readable only by the current user. The data is
     * written to a temporary file (created with owner-only permissions) and
     * then atomically moved into place, so the auth token is never exposed
     * and readers never see a partially written file.
     */
    private static void writeSecurely(ObjectMapper mapper, Path target, ObjectNode data) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        // createTempFile uses owner-only permissions on POSIX file systems
        Path tmp = Files.createTempFile(dir, ".lock-", ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), data);
            restrictToOwner(tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }
    
    /**
     * Removes the lock file when NetBeans shuts down.
     */
    public void removeLockFile() {
        if (lockFileCreated && Files.exists(lockFilePath)) {
            try {
                Files.delete(lockFilePath);
                lockFileCreated = false;
                LOGGER.log(Level.INFO, "Removed Claude Code lock file");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to remove lock file", e);
            }
        }
    }
    
    /**
     * Gets the path where the lock file should be created.
     * 
     * @param port The WebSocket server port
     * @return Path to the lock file
     */
    private Path getLockFilePath(int port) {
        String homeDir = System.getProperty("user.home");
        return Paths.get(homeDir, CLAUDE_DIR, IDE_DIR, port + ".lock");
    }
    
    /**
     * Gets the list of workspace folders from open projects.
     * 
     * @return List of workspace folder paths
     */
    private List<String> getWorkspaceFolders() {
        List<String> folders = new ArrayList<>();
        
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        for (Project project : openProjects) {
            FileObject projectDir = project.getProjectDirectory();
            if (projectDir != null) {
                File projectFile = new File(projectDir.getPath());
                folders.add(projectFile.getAbsolutePath());
            }
        }
        
        // If no projects are open, use the user home directory
        if (folders.isEmpty()) {
            folders.add(System.getProperty("user.home"));
        }
        
        return folders;
    }
    
    /**
     * Gets the current NetBeans process ID.
     * 
     * @return Process ID
     */
    public static long getCurrentProcessId() {
        return ProcessHandle.current().pid();
    }
    
    /**
     * Checks if the lock file exists and is valid.
     * 
     * @return true if lock file exists and is readable
     */
    public boolean isLockFileValid() {
        return lockFileCreated && Files.exists(lockFilePath) && Files.isReadable(lockFilePath);
    }
}
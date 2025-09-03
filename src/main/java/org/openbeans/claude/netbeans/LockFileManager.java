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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
     */
    public void createLockFile(int port, long pid) {
        try {
            // Set the lock file path based on the port
            this.lockFilePath = getLockFilePath(port);
            
            // Ensure the directory exists
            Files.createDirectories(lockFilePath.getParent());
            
            // Create lock file content
            ObjectNode lockData = objectMapper.createObjectNode();
            lockData.put("pid", pid);
            lockData.put("ideName", "NetBeans");
            lockData.put("transport", "ws");
            lockData.put("authToken", UUID.randomUUID().toString());
            
            // Add workspace folders
            List<String> workspaceFolders = getWorkspaceFolders();
            lockData.set("workspaceFolders", objectMapper.valueToTree(workspaceFolders));
            
            // Write the lock file
            objectMapper.writeValue(lockFilePath.toFile(), lockData);
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
            objectMapper.writeValue(lockFilePath.toFile(), lockData);
            
            LOGGER.log(Level.FINE, "Updated lock file with new workspace folders");
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update lock file", e);
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
        try {
            String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(processName.split("@")[0]);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not determine process ID", e);
            return -1;
        }
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
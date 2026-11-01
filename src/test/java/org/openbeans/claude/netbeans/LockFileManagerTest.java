package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LockFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    public void writesProvidedTokenWithOwnerOnlyPermissions() throws Exception {
        Path lockFile = tempDir.resolve("ide").resolve("1234.lock");

        LockFileManager.writeLockFile(lockFile, 42L, "secret-token", List.of("/some/project"));

        JsonNode data = new ObjectMapper().readTree(lockFile.toFile());
        assertEquals("secret-token", data.get("authToken").asText());
        assertEquals(42L, data.get("pid").asLong());
        assertEquals("ws", data.get("transport").asText());
        assertEquals("/some/project", data.get("workspaceFolders").get(0).asText());

        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(lockFile));
    }

    @Test
    public void overwritesStaleLockFile() throws Exception {
        Path lockFile = tempDir.resolve("1234.lock");
        Files.writeString(lockFile, "{\"authToken\":\"old\"}");

        LockFileManager.writeLockFile(lockFile, 1L, "new", List.of());

        assertEquals("new", new ObjectMapper().readTree(lockFile.toFile()).get("authToken").asText());
    }

    @Test
    public void removesOnlyStaleNetBeansLockFiles() throws Exception {
        long deadPid = deadPid();
        Path alive = tempDir.resolve("1.lock");
        Path stale = tempDir.resolve("2.lock");
        Path otherIde = tempDir.resolve("3.lock");
        Path garbage = tempDir.resolve("4.lock");
        LockFileManager.writeLockFile(alive, ProcessHandle.current().pid(), "t", List.of());
        LockFileManager.writeLockFile(stale, deadPid, "t", List.of());
        Files.writeString(otherIde, "{\"pid\":" + deadPid + ",\"ideName\":\"Visual Studio Code\"}");
        Files.writeString(garbage, "not json");

        LockFileManager.cleanStaleLockFiles(tempDir);

        assertTrue(Files.exists(alive));
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(otherIde), "other IDEs' lock files must not be touched");
        assertTrue(Files.exists(garbage));
    }

    /** A pid that belongs to no running process. */
    private static long deadPid() throws Exception {
        Process p = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        p.waitFor();
        return p.pid();
    }
}

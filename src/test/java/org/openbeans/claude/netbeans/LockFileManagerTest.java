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
}

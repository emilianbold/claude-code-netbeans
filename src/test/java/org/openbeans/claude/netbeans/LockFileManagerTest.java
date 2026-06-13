package org.openbeans.claude.netbeans;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LockFileManagerTest {

    @Test
    public void getCurrentProcessId_returnsPositiveValue() {
        long pid = LockFileManager.getCurrentProcessId();
        assertTrue(pid > 0, "Expected positive PID, got: " + pid);
    }

    @Test
    public void isLockFileValid_returnsFalse_onNewInstance() {
        assertFalse(new LockFileManager().isLockFileValid());
    }

    @Test
    public void removeLockFile_doesNotThrow_whenNothingCreated() {
        assertDoesNotThrow(() -> new LockFileManager().removeLockFile());
    }

    @Test
    public void updateLockFile_doesNothing_whenNothingCreated() {
        assertDoesNotThrow(() -> new LockFileManager().updateLockFile());
    }

    @Test
    public void isLockFileValid_returnsTrue_whenFileExists() throws Exception {
        Path tmp = Files.createTempFile("test-lock", ".lock");
        try {
            LockFileManager mgr = new LockFileManager();
            setField(mgr, "lockFilePath", tmp);
            setField(mgr, "lockFileCreated", true);
            assertTrue(mgr.isLockFileValid());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void removeLockFile_deletesFile_whenCreated() throws Exception {
        Path tmp = Files.createTempFile("test-lock", ".lock");
        LockFileManager mgr = new LockFileManager();
        setField(mgr, "lockFilePath", tmp);
        setField(mgr, "lockFileCreated", true);

        assertTrue(Files.exists(tmp));
        mgr.removeLockFile();
        assertFalse(Files.exists(tmp));
        assertFalse(mgr.isLockFileValid());
    }

    @Test
    public void createLockFile_createsFileOnDisk() throws Exception {
        int testPort = 19999;
        Path expectedPath = Paths.get(System.getProperty("user.home"), ".claude", "ide", testPort + ".lock");
        LockFileManager mgr = new LockFileManager();
        try {
            mgr.createLockFile(testPort, LockFileManager.getCurrentProcessId());
            // OpenProjects returns empty in test env -> lock file should be written
            assertTrue(mgr.isLockFileValid());
            assertTrue(Files.exists(expectedPath));
        } finally {
            mgr.removeLockFile();
            Files.deleteIfExists(expectedPath);
        }
    }

    @Test
    public void updateLockFile_afterCreate_doesNotThrow() throws Exception {
        int testPort = 19998;
        Path expectedPath = Paths.get(System.getProperty("user.home"), ".claude", "ide", testPort + ".lock");
        LockFileManager mgr = new LockFileManager();
        try {
            mgr.createLockFile(testPort, LockFileManager.getCurrentProcessId());
            if (mgr.isLockFileValid()) {
                assertDoesNotThrow(() -> mgr.updateLockFile());
            }
        } finally {
            mgr.removeLockFile();
            Files.deleteIfExists(expectedPath);
        }
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}

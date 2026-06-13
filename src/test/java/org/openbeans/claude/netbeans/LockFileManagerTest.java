package org.openbeans.claude.netbeans;

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
}

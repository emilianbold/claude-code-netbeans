package org.openbeans.claude.netbeans.tools;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;
import static org.junit.jupiter.api.Assertions.*;

public class DiffTabTrackerTest {

    @Test
    public void testRegisterAndIsTracked() {
        String tab = "test_register_" + System.nanoTime();
        DiffTabTracker.register(tab, r -> {});
        try {
            assertTrue(DiffTabTracker.isTracked(tab));
        } finally {
            DiffTabTracker.remove(tab);
        }
    }

    @Test
    public void testIsTracked_unregistered() {
        assertFalse(DiffTabTracker.isTracked("nonexistent-tab-xyz"));
    }

    @Test
    public void testRemove_returnsHandlerAndUntracks() {
        String tab = "test_remove_" + System.nanoTime();
        DiffTabTracker.register(tab, r -> {});
        AsyncHandler<?> removed = DiffTabTracker.remove(tab);
        assertNotNull(removed);
        assertFalse(DiffTabTracker.isTracked(tab));
    }

    @Test
    public void testRemove_notRegistered_returnsNull() {
        assertNull(DiffTabTracker.remove("nonexistent-tab-remove-xyz"));
    }

    @Test
    public void testSetResponse_invokesHandler() {
        String tab = "test_resp_" + System.nanoTime();
        AtomicReference<OpenDiffResult> received = new AtomicReference<>();
        DiffTabTracker.register(tab, r -> received.set((OpenDiffResult) r));

        OpenDiffResult result = new OpenDiffResult(List.of(new Content("text", "FILE_SAVED")));
        DiffTabTracker.setResponse(tab, result);

        assertNotNull(received.get());
        DiffTabTracker.remove(tab);
    }

    @Test
    public void testSetResponse_noHandler_doesNotThrow() {
        OpenDiffResult result = new OpenDiffResult(List.of(new Content("text", "FILE_SAVED")));
        assertDoesNotThrow(() -> DiffTabTracker.setResponse("no-such-tab-xyz", result));
    }
}

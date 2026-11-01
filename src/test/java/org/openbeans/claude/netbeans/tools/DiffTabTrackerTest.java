package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;
import static org.junit.jupiter.api.Assertions.*;

public class DiffTabTrackerTest {

    /** Records every response sent for one request. */
    private static class RecordingHandler implements AsyncHandler<OpenDiffResult> {
        final List<OpenDiffResult> responses = new ArrayList<>();

        @Override
        public void sendResponse(OpenDiffResult result) {
            responses.add(result);
        }

        String onlyStatus() {
            assertEquals(1, responses.size(), "expected exactly one response");
            return responses.get(0).getContent().get(0).getText();
        }
    }

    private static OpenDiffResult saved() {
        return new OpenDiffResult(List.of(new Content("text", "FILE_SAVED"), new Content("text", "new")));
    }

    @BeforeEach
    public void reset() {
        DiffTabTracker.clear();
    }

    @Test
    public void approveThenCloseSendsOnlyOneResponse() {
        Object tab = new Object();
        RecordingHandler handler = new RecordingHandler();
        DiffTabTracker.register(tab, "diff", handler);

        assertTrue(DiffTabTracker.accept(tab, saved()));
        // Claude Code closes the tab after approval
        assertFalse(DiffTabTracker.reject(tab));

        assertEquals("FILE_SAVED", handler.onlyStatus());
    }

    @Test
    public void approvingTwiceSendsOnlyOneResponse() {
        Object tab = new Object();
        RecordingHandler handler = new RecordingHandler();
        DiffTabTracker.register(tab, "diff", handler);

        DiffTabTracker.accept(tab, saved());
        DiffTabTracker.accept(tab, saved());

        assertEquals("FILE_SAVED", handler.onlyStatus());
    }

    @Test
    public void closingPendingDiffRejectsIt() {
        Object tab = new Object();
        RecordingHandler handler = new RecordingHandler();
        DiffTabTracker.register(tab, "diff", handler);

        assertTrue(DiffTabTracker.reject(tab));

        assertEquals("DIFF_REJECTED", handler.onlyStatus());
        assertEquals("diff", handler.responses.get(0).getContent().get(1).getText());
        assertFalse(DiffTabTracker.isPending(tab));
    }

    @Test
    public void sameTabNameSupersedesPreviousDiff() {
        Object first = new Object();
        Object second = new Object();
        RecordingHandler firstHandler = new RecordingHandler();
        RecordingHandler secondHandler = new RecordingHandler();

        DiffTabTracker.register(first, "diff", firstHandler);
        List<Object> superseded = DiffTabTracker.register(second, "diff", secondHandler);

        assertEquals(List.of(first), superseded);
        assertEquals("DIFF_REJECTED", firstHandler.onlyStatus());

        // Closing the superseded tab must not answer either request again
        assertFalse(DiffTabTracker.reject(first));
        assertTrue(DiffTabTracker.accept(second, saved()));
        assertEquals("FILE_SAVED", secondHandler.onlyStatus());
        assertEquals(1, firstHandler.responses.size());
    }

    @Test
    public void untrackedTabsAreIgnored() {
        assertFalse(DiffTabTracker.reject(new Object()));
        assertFalse(DiffTabTracker.accept(new Object(), saved()));
    }
}

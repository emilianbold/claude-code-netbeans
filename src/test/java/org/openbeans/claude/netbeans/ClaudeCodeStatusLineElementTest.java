package org.openbeans.claude.netbeans;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.junit.jupiter.api.Test;
import org.openide.util.ChangeSupport;
import static org.junit.jupiter.api.Assertions.*;

public class ClaudeCodeStatusLineElementTest {

    private static class FakeStatus implements ClaudeCodeStatusService {
        final ChangeSupport cs = new ChangeSupport(this);
        boolean running;
        boolean lock;

        @Override public String getStatus() { return "status"; }
        @Override public boolean isServerRunning() { return running; }
        @Override public int getServerPort() { return running ? 8990 : -1; }
        @Override public boolean isLockFileValid() { return lock; }
        @Override public void addChangeListener(ChangeListener l) { cs.addChangeListener(l); }
        @Override public void removeChangeListener(ChangeListener l) { cs.removeChangeListener(l); }
    }

    @Test
    public void labelFollowsServerState() throws Exception {
        FakeStatus status = new FakeStatus();
        ClaudeCodeStatusLineElement element = new ClaudeCodeStatusLineElement(status);
        JLabel label = (JLabel) element.getStatusLineElement();
        assertEquals("Claude ✗", label.getText());

        status.running = true;
        status.lock = true;
        status.cs.fireChange();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals("Claude ✓", label.getText());

        status.running = false;
        status.lock = false;
        status.cs.fireChange();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals("Claude ✗", label.getText());
    }
}

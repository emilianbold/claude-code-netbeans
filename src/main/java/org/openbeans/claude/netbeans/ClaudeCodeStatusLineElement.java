package org.openbeans.claude.netbeans;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Status bar element for Claude Code integration status.
 * Shows a visual indicator in the NetBeans status bar with tooltip information.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 100)
public class ClaudeCodeStatusLineElement implements StatusLineElementProvider {
    
    private JLabel statusLabel;
    private ClaudeCodeStatusService statusService;
    
    public ClaudeCodeStatusLineElement() {
        this(Lookup.getDefault().lookup(ClaudeCodeStatusService.class));
    }

    ClaudeCodeStatusLineElement(ClaudeCodeStatusService statusService) {
        this.statusService = statusService;
        initializeStatusLabel();
        if (statusService != null) {
            // The server starts asynchronously, after this element is created
            statusService.addChangeListener(e -> SwingUtilities.invokeLater(this::updateStatusDisplay));
        }
    }
    
    private void initializeStatusLabel() {
        statusLabel = new JLabel();
        statusLabel.setText("Claude");
        statusLabel.setToolTipText("Claude Code: Initializing...");
        
        // Add mouse listener for click interactions (future enhancement)
        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Could show status dialog or perform action
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                updateTooltip();
            }
        });
        
        updateStatusDisplay();
    }
    
    private void updateStatusDisplay() {
        if (statusService != null) {
            boolean isRunning = statusService.isServerRunning();
            boolean lockValid = statusService.isLockFileValid();
            
            if (isRunning && lockValid) {
                statusLabel.setText("Claude ✓");
            } else if (isRunning) {
                statusLabel.setText("Claude ~");
            } else {
                statusLabel.setText("Claude ✗");
            }
            
            updateTooltip();
        }
    }
    
    private void updateTooltip() {
        if (statusService != null) {
            String status = statusService.getStatus();
            statusLabel.setToolTipText("<html>" + status.replace("\n", "<br>") + "</html>");
        } else {
            statusLabel.setToolTipText("Claude Code: Service not available");
        }
    }
    
    @Override
    public Component getStatusLineElement() {
        return statusLabel;
    }
    
}
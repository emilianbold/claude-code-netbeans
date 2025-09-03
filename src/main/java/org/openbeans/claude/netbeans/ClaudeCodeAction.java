package org.openbeans.claude.netbeans;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Action to show Claude Code integration status and provide manual controls.
 */
@ActionID(
    category = "Tools",
    id = "org.openbeans.claude.netbeans.ClaudeCodeAction"
)
@ActionRegistration(
    displayName = "#CTL_ClaudeCodeAction"
)
@ActionReference(path = "Menu/Tools", position = 1200)
@Messages("CTL_ClaudeCodeAction=Claude Code Status")
public final class ClaudeCodeAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            // Get the status service from global lookup
            ClaudeCodeStatusService statusService = Lookup.getDefault().lookup(ClaudeCodeStatusService.class);
            
            if (statusService != null) {
                String status = statusService.getStatus();
                
                // Show status dialog
                NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
                    status,
                    NotifyDescriptor.INFORMATION_MESSAGE
                );
                msg.setTitle("Claude Code Integration Status");
                DialogDisplayer.getDefault().notify(msg);
            } else {
                // Service not available
                NotifyDescriptor.Message error = new NotifyDescriptor.Message(
                    "Claude Code integration is not running or not properly initialized.",
                    NotifyDescriptor.WARNING_MESSAGE
                );
                error.setTitle("Claude Code Not Available");
                DialogDisplayer.getDefault().notify(error);
            }
            
        } catch (Exception ex) {
            NotifyDescriptor.Message error = new NotifyDescriptor.Message(
                "Error getting Claude Code status: " + ex.getMessage(),
                NotifyDescriptor.ERROR_MESSAGE
            );
            error.setTitle("Claude Code Error");
            DialogDisplayer.getDefault().notify(error);
        }
    }
}
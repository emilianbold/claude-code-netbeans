package org.openbeans.claude.netbeans;

import javax.swing.event.ChangeListener;

/**
 * Service interface for accessing Claude Code integration status.
 * This service is registered in the global lookup to provide status information
 * without requiring direct access to the ModuleInstall singleton.
 */
public interface ClaudeCodeStatusService {
    
    /**
     * Gets the current status of the Claude Code integration.
     * 
     * @return status information as a formatted string
     */
    String getStatus();
    
    /**
     * Checks if the MCP server is currently running.
     * 
     * @return true if the server is running, false otherwise
     */
    boolean isServerRunning();
    
    /**
     * Gets the port number the MCP server is running on.
     * 
     * @return port number, or -1 if server is not running
     */
    int getServerPort();
    
    /**
     * Checks if the lock file is valid and accessible.
     * 
     * @return true if lock file is valid, false otherwise
     */
    boolean isLockFileValid();

    /**
     * Registers a listener notified whenever the status changes (server
     * started or stopped, lock file created or removed). Listeners may be
     * called on any thread.
     *
     * @param listener the listener
     */
    void addChangeListener(ChangeListener listener);

    /**
     * @param listener a previously added listener
     */
    void removeChangeListener(ChangeListener listener);
}
package org.openbeans.claude.netbeans;

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
}
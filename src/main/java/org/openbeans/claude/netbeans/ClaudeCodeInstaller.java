package org.openbeans.claude.netbeans;

import org.openide.modules.ModuleInstall;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.ui.OpenProjects;

/**
 * Manages the lifecycle of the Claude Code NetBeans plugin.
 * Handles installation, startup, and shutdown of the plugin components.
 */
@ServiceProvider(service = ClaudeCodeStatusService.class)
public class ClaudeCodeInstaller extends ModuleInstall implements PropertyChangeListener, ClaudeCodeStatusService {
    
    private static final Logger LOGGER = Logger.getLogger(ClaudeCodeInstaller.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("ClaudeCode", 1);
    
    private WebSocketMCPServer mcpServer;
    private NetBeansMCPHandler mcpHandler;
    private LockFileManager lockFileManager;
    
    /**
     * Called when the module is first installed.
     */
    @Override
    public void restored() {
        LOGGER.info("Claude Code NetBeans plugin is starting up...");
        
        // Initialize components
        initializeComponents();
        
        // Start the MCP server
        startMCPServer();
        
        // Listen for project changes to update lock file
         OpenProjects.getDefault().addPropertyChangeListener(this);
        
        LOGGER.info("Claude Code NetBeans plugin started successfully");
    }
    
    /**
     * Called when the module is being uninstalled.
     */
    @Override
    public void uninstalled() {
        LOGGER.info("Claude Code NetBeans plugin is shutting down...");
        
         OpenProjects.getDefault().removePropertyChangeListener(this);
        
        // Stop MCP server
        stopMCPServer();
        
        // Clean up lock file
        if (lockFileManager != null) {
            lockFileManager.removeLockFile();
        }
        
        LOGGER.info("Claude Code NetBeans plugin shut down complete");
    }
    
    /**
     * Called when NetBeans is closing.
     */
    @Override
    public void close() {
        uninstalled();
    }
    
    /**
     * Handles property changes, particularly open projects changes.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
         if (OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
             // Update lock file when projects change
             RP.post(() -> {
                 if (lockFileManager != null) {
                     lockFileManager.updateLockFile();
                 }
             });
         }
    }
    
    /**
     * Initializes all plugin components.
     */
    private void initializeComponents() {
        try {
            // Create MCP handler
            mcpHandler = new NetBeansMCPHandler();
            
            // Create WebSocket server
            mcpServer = new WebSocketMCPServer(mcpHandler);
            
            // Create lock file manager
            lockFileManager = new LockFileManager();
            
            LOGGER.info("Claude Code components initialized");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Claude Code components", e);
            Exceptions.printStackTrace(e);
        }
    }
    
    /**
     * Starts the MCP WebSocket server and creates the lock file.
     */
    private void startMCPServer() {
        RP.post(() -> {
            try {
                // Start the WebSocket server
                if (mcpServer.start()) {
                    int port = mcpServer.getPort();
                    long pid = LockFileManager.getCurrentProcessId();
                    
                    // Create lock file with server information
                    lockFileManager.createLockFile(port, pid);
                    
                    LOGGER.log(Level.INFO, "Claude Code MCP server started on port {0}, PID {1}", 
                              new Object[]{port, pid});
                } else {
                    LOGGER.severe("Failed to start Claude Code MCP server");
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error starting Claude Code MCP server", e);
                Exceptions.printStackTrace(e);
            }
        });
    }
    
    /**
     * Stops the MCP WebSocket server.
     */
    private void stopMCPServer() {
        if (mcpServer != null && mcpServer.isRunning()) {
            try {
                mcpServer.stop();
                LOGGER.info("Claude Code MCP server stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping Claude Code MCP server", e);
            }
        }
    }
    
    /**
     * Gets the current status of the Claude Code integration.
     * 
     * @return status information
     */
    @Override
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("<b>Claude Code NetBeans Integration</b><br>");
        
        if (mcpServer != null) {
            if (mcpServer.isRunning()) {
                status.append("🟢 MCP Server: Running on port ").append(mcpServer.getPort()).append("<br>");
            } else {
                status.append("🔴 MCP Server: Stopped<br>");
            }
        } else {
            status.append("⚪ MCP Server: Not initialized<br>");
        }
        
        if (lockFileManager != null) {
            if (lockFileManager.isLockFileValid()) {
                status.append("🟢 Lock File: Created<br>");
            } else {
                status.append("🔴 Lock File: Not found<br>");
            }
        } else {
            status.append("⚪ Lock File: Not managed<br>");
        }
        
        status.append("🔧 Process ID: ").append(LockFileManager.getCurrentProcessId());
        
        return status.toString();
    }
    
    /**
     * Checks if the MCP server is currently running.
     * 
     * @return true if the server is running, false otherwise
     */
    @Override
    public boolean isServerRunning() {
        return mcpServer != null && mcpServer.isRunning();
    }
    
    /**
     * Gets the port number the MCP server is running on.
     * 
     * @return port number, or -1 if server is not running
     */
    @Override
    public int getServerPort() {
        if (mcpServer != null && mcpServer.isRunning()) {
            return mcpServer.getPort();
        }
        return -1;
    }
    
    /**
     * Checks if the lock file is valid and accessible.
     * 
     * @return true if lock file is valid, false otherwise
     */
    @Override
    public boolean isLockFileValid() {
        return lockFileManager != null && lockFileManager.isLockFileValid();
    }
}
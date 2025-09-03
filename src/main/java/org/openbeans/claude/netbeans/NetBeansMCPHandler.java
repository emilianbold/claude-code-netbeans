package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.websocket.api.Session;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import org.openide.text.NbDocument;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.modules.Places;

/**
 * Handles Model Context Protocol messages and provides NetBeans IDE capabilities
 * to Claude Code through MCP primitives (Tools, Resources, Prompts).
 */
public class NetBeansMCPHandler {
    
    private static final Logger LOGGER = Logger.getLogger(NetBeansMCPHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Session webSocketSession;
    
    // Selection tracking
    private final Map<JTextComponent, CaretListener> selectionListeners = new WeakHashMap<>();
    private PropertyChangeListener topComponentListener;
    private JTextComponent currentTextComponent;
    
    /**
     * Handles incoming MCP messages and routes them to appropriate handlers.
     * 
     * @param message the JSON-RPC message
     * @return response JSON string, or null if no response needed
     */
    public String handleMessage(JsonNode message) {
        try {
            String method = message.get("method").asText();
            JsonNode params = message.get("params");
            String id = message.has("id") ? message.get("id").asText() : null;
            
            LOGGER.log(Level.FINE, "Processing MCP method: {0}", method);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.put("id", id);
            }
            
            switch (method) {
                case "initialize":
                    response.set("result", handleInitialize(params));
                    break;
                    
                case "tools/list":
                    response.set("result", handleToolsList());
                    break;
                    
                case "tools/call":
                    response.set("result", handleToolsCall(params));
                    break;
                    
                case "resources/list":
                    response.set("result", handleResourcesList());
                    break;
                    
                case "resources/read":
                    response.set("result", handleResourcesRead(params));
                    break;
                    
                case "prompts/list":
                    response.set("result", handlePromptsList());
                    break;
                    
                default:
                    LOGGER.log(Level.WARNING, "Unknown MCP method: {0}", method);
                    return createErrorResponse(id, -32601, "Method not found", method);
            }
            
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling MCP message", e);
            return createErrorResponse(null, -32603, "Internal error", e.getMessage());
        }
    }
    
    /**
     * Handles MCP initialize request.
     */
    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        
        ObjectNode capabilities = objectMapper.createObjectNode();
        
        ObjectNode toolsCapability = objectMapper.createObjectNode();
        toolsCapability.put("listChanged", true);
        capabilities.set("tools", toolsCapability);
        
        ObjectNode resourcesCapability = objectMapper.createObjectNode();
        resourcesCapability.put("subscribe", true);
        resourcesCapability.put("listChanged", true);
        capabilities.set("resources", resourcesCapability);
        
        ObjectNode promptsCapability = objectMapper.createObjectNode();
        promptsCapability.put("listChanged", true);
        capabilities.set("prompts", promptsCapability);
        
        result.set("capabilities", capabilities);
        
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "netbeans-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        
        return result;
    }
    
    /**
     * Lists available tools (executable functions).
     */
    private JsonNode handleToolsList() {
        ArrayNode tools = objectMapper.createArrayNode();
        
        // Core Claude Code tools
        tools.add(createTool("openFile", "Opens a file in the editor", 
            "path", "string", "Path to the file to open",
            "preview", "boolean", "Whether to open in preview mode"));
        
        tools.add(createTool("getWorkspaceFolders", "Get list of workspace folders (open projects)"));
        
        tools.add(createTool("getOpenEditors", "Get list of currently open editor tabs"));
        
        tools.add(createTool("getCurrentSelection", "Get the current text selection in the active editor"));
        
        // Additional file operations (Claude Code compatible)
        tools.add(createTool("read_file", "Read file contents (within open projects only)", 
            "path", "string", "Path to the file to read (must be within an open project)"));
        
        tools.add(createTool("write_file", "Write file contents (within open projects only)",
            "path", "string", "Path to the file to write (must be within an open project)",
            "content", "string", "Content to write to the file"));
        
        tools.add(createTool("list_files", "List files in directory (within open projects only)",
            "path", "string", "Directory path to list (must be within an open project)"));
        
        tools.add(createTool("close_tab", "Close an open editor tab",
            "path", "string", "Path to the file to close"));
        
        tools.add(createTool("getDiagnostics", "Get diagnostics information about the IDE and environment"));
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        return result;
    }
    
    /**
     * Handles tool call requests.
     */
    private JsonNode handleToolsCall(JsonNode params) {
        String toolName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");
        
        try {
            switch (toolName) {
                // Core Claude Code tools
                case "openFile":
                    return handleOpenFile(arguments.get("path").asText(), 
                                        arguments.has("preview") ? arguments.get("preview").asBoolean() : false);
                    
                case "getWorkspaceFolders":
                    return handleGetWorkspaceFolders();
                    
                case "getOpenEditors":
                    return handleGetOpenEditors();
                    
                case "getCurrentSelection":
                    return handleGetCurrentSelection();
                    
                // File operations
                case "read_file":
                    JsonNode pathNode = arguments.get("path");
                    if (pathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: path");
                    }
                    return handleReadFile(pathNode.asText());
                    
                case "write_file":
                    JsonNode writePathNode = arguments.get("path");
                    JsonNode contentNode = arguments.get("content");
                    if (writePathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: path");
                    }
                    if (contentNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: content");
                    }
                    return handleWriteFile(writePathNode.asText(), contentNode.asText());
                    
                case "list_files":
                    JsonNode listPathNode = arguments.get("path");
                    if (listPathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: path");
                    }
                    return handleListFiles(listPathNode.asText());
                    
                case "close_tab":
                    JsonNode closePathNode = arguments.get("path");
                    if (closePathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: path");
                    }
                    return handleCloseTab(closePathNode.asText());
                    
                case "getDiagnostics":
                    return handleGetDiagnostics();
                    
                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing tool: " + toolName, e);
            
            ObjectNode result = objectNode();
            result.put("isError", true);
            result.put("content", "Error: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Lists available resources.
     */
    private JsonNode handleResourcesList() {
        ArrayNode resources = objectMapper.createArrayNode();
        
         Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
         for (Project project : openProjects) {
             ObjectNode resource = objectMapper.createObjectNode();
             resource.put("uri", "project://" + project.getProjectDirectory().getPath());
             resource.put("name", ProjectUtils.getInformation(project).getDisplayName());
             resource.put("description", "NetBeans project: " + ProjectUtils.getInformation(project).getDisplayName());
             resource.put("mimeType", "application/json");
             resources.add(resource);
         }
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("resources", resources);
        return result;
    }
    
    /**
     * Reads a resource.
     */
    private JsonNode handleResourcesRead(JsonNode params) {
        String uri = params.get("uri").asText();
        
        if (uri.startsWith("project://")) {
            String projectPath = uri.substring("project://".length());
            return getProjectInfo(projectPath);
        }
        
        throw new IllegalArgumentException("Unknown resource URI: " + uri);
    }
    
    /**
     * Lists available prompts.
     */
    private JsonNode handlePromptsList() {
        ArrayNode prompts = objectMapper.createArrayNode();
        
        ObjectNode codeReviewPrompt = objectMapper.createObjectNode();
        codeReviewPrompt.put("name", "code_review");
        codeReviewPrompt.put("description", "Review code in NetBeans project");
        prompts.add(codeReviewPrompt);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("prompts", prompts);
        return result;
    }
    
    // Tool implementation methods
    
    private JsonNode handleReadFile(String filePath) throws IOException {
        // Security check: Only allow reading files within open project directories
        if (!isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File read denied: Path is not within any open project directory: " + filePath);
        }
        
        Path path = Paths.get(filePath);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        
        ObjectNode result = objectNode();
        result.put("content", content);
        result.put("mimeType", "text/plain");
        return result;
    }
    
    private JsonNode handleWriteFile(String filePath, String content) throws IOException {
        // Security check: Only allow writing to files within open project directories
        if (!isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File write denied: Path is not within any open project directory: " + filePath);
        }
        
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        
        ObjectNode result = objectNode();
        result.put("content", "File written successfully: " + filePath);
        return result;
    }
    
    private JsonNode handleListFiles(String dirPath) {
        // Security check: Only allow listing directories within open project directories
        if (!isPathWithinOpenProjects(dirPath)) {
            throw new SecurityException("Directory listing denied: Path is not within any open project directory: " + dirPath);
        }
        
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        
        ArrayNode fileList = objectMapper.createArrayNode();
        if (files != null) {
            for (File file : files) {
                ObjectNode fileInfo = objectMapper.createObjectNode();
                fileInfo.put("name", file.getName());
                fileInfo.put("path", file.getAbsolutePath());
                fileInfo.put("isDirectory", file.isDirectory());
                fileInfo.put("size", file.length());
                fileList.add(fileInfo);
            }
        }
        
        ObjectNode result = objectNode();
        result.set("content", fileList);
        return result;
    }
    
    private JsonNode handleGetOpenProjects() {
        ArrayNode projects = objectMapper.createArrayNode();
        
         Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
         for (Project project : openProjects) {
             ObjectNode projectInfo = objectMapper.createObjectNode();
             projectInfo.put("name", ProjectUtils.getInformation(project).getDisplayName());
             projectInfo.put("path", project.getProjectDirectory().getPath());
             projects.add(projectInfo);
         }
        
        ObjectNode result = objectNode();
        result.set("content", projects);
        return result;
    }
    
    private JsonNode handleGetProjectFiles(String projectPath) {
        FileObject projectDir = FileUtil.toFileObject(new File(projectPath));
        if (projectDir == null) {
            throw new IllegalArgumentException("Project not found: " + projectPath);
        }
        
        ArrayNode files = objectMapper.createArrayNode();
        collectProjectFiles(projectDir, files, "");
        
        ObjectNode result = objectNode();
        result.set("content", files);
        return result;
    }
    
    private JsonNode handleGetOpenDocuments() {
        ArrayNode documents = objectMapper.createArrayNode();
        
        try {
            // Use TopComponent registry to get open editor nodes
            Node[] nodes = TopComponent.getRegistry().getCurrentNodes();
            for (Node node : nodes) {
                EditorCookie editorCookie = node.getLookup().lookup(EditorCookie.class);
                if (editorCookie != null) {
                    DataObject dataObject = node.getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        FileObject fileObject = dataObject.getPrimaryFile();
                        if (fileObject != null) {
                            ObjectNode docInfo = objectMapper.createObjectNode();
                            docInfo.put("name", fileObject.getName());
                            docInfo.put("path", fileObject.getPath());
                            docInfo.put("extension", fileObject.getExt());
                            docInfo.put("mimeType", fileObject.getMIMEType());
                            
                            // Get the project owner using FileOwnerQuery
                            Project owner = FileOwnerQuery.getOwner(fileObject);
                            if (owner != null) {
                                docInfo.put("projectName", ProjectUtils.getInformation(owner).getDisplayName());
                                docInfo.put("projectPath", owner.getProjectDirectory().getPath());
                            }
                            
                            documents.add(docInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting open documents", e);
        }
        
        ObjectNode result = objectNode();
        result.set("content", documents);
        return result;
    }
    
    private JsonNode handleGetDocumentContent(String filePath) {
        try {
            FileObject fileObject = FileUtil.toFileObject(new File(filePath));
            if (fileObject != null) {
                DataObject dataObject = DataObject.find(fileObject);
                EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                
                if (editorCookie != null) {
                    Document doc = editorCookie.getDocument();
                    if (doc != null) {
                        String content = doc.getText(0, doc.getLength());
                        ObjectNode result = objectNode();
                        result.put("content", content);
                        return result;
                    }
                }
            }
            
            // Fallback to file reading
            return handleReadFile(filePath);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get document content: " + e.getMessage(), e);
        }
    }
    
    // Claude Code specific tool implementations
    
    private JsonNode handleOpenFile(String filePath, boolean preview) {
        try {
            // Security check: Only allow opening files within open project directories
            if (!isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File open denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("File does not exist: " + filePath);
            }
            
            FileObject fileObject = FileUtil.toFileObject(file);
            if (fileObject != null) {
                DataObject dataObject = DataObject.find(fileObject);
                EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                
                if (editorCookie != null) {
                    // Open the file in NetBeans editor
                    editorCookie.open();
                    
                    ObjectNode result = objectNode();
                    result.put("content", "File opened successfully: " + filePath);
                    result.put("preview", preview);
                    return result;
                }
            }
            
            throw new RuntimeException("Failed to open file in editor");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to open file: " + e.getMessage(), e);
        }
    }
    
    private JsonNode handleGetWorkspaceFolders() {
        ArrayNode folders = objectMapper.createArrayNode();
        
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        for (Project project : openProjects) {
            ObjectNode folderInfo = objectMapper.createObjectNode();
            folderInfo.put("name", ProjectUtils.getInformation(project).getDisplayName());
            folderInfo.put("uri", "file://" + project.getProjectDirectory().getPath());
            folders.add(folderInfo);
        }
        
        ObjectNode result = objectNode();
        result.set("content", folders);
        return result;
    }
    
    private JsonNode handleGetOpenEditors() {
        return handleGetOpenDocuments(); // Reuse existing implementation
    }
    
    private JsonNode handleGetCurrentSelection() {
        try {
            // Get the current active editor
            TopComponent activeTC = TopComponent.getRegistry().getActivated();
            if (activeTC != null) {
                Node[] nodes = activeTC.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
                    if (editorCookie != null) {
                        // Get the opened editor panes
                        JTextComponent[] panes = editorCookie.getOpenedPanes();
                        if (panes != null && panes.length > 0) {
                            // Use the first pane (usually the active one)
                            JTextComponent textComponent = panes[0];
                            
                            if (textComponent != null && textComponent.getDocument() instanceof StyledDocument) {
                                StyledDocument doc = (StyledDocument) textComponent.getDocument();
                                String selectedText = textComponent.getSelectedText();
                                int selectionStart = textComponent.getSelectionStart();
                                int selectionEnd = textComponent.getSelectionEnd();
                                
                                ObjectNode result = objectNode();
                                
                                if (selectedText != null && !selectedText.isEmpty()) {
                                    result.put("content", selectedText);
                                    
                                    // Use NbDocument utility methods for line/column calculation
                                    int startLine = NbDocument.findLineNumber(doc, selectionStart) + 1; // Convert to 1-based
                                    int startColumn = NbDocument.findLineColumn(doc, selectionStart);
                                    int endLine = NbDocument.findLineNumber(doc, selectionEnd) + 1; // Convert to 1-based
                                    int endColumn = NbDocument.findLineColumn(doc, selectionEnd);
                                    
                                    result.put("startLine", startLine);
                                    result.put("startColumn", startColumn);
                                    result.put("endLine", endLine);
                                    result.put("endColumn", endColumn);
                                    
                                    // Add file path if available
                                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                                    if (dataObject != null) {
                                        FileObject fileObject = dataObject.getPrimaryFile();
                                        if (fileObject != null) {
                                            result.put("filePath", fileObject.getPath());
                                        }
                                    }
                                } else {
                                    // No selection
                                    result.put("content", "");
                                    result.put("startLine", 0);
                                    result.put("startColumn", 0);
                                    result.put("endLine", 0);
                                    result.put("endColumn", 0);
                                }
                                
                                return result;
                            }
                        }
                    }
                }
            }
            
            ObjectNode result = objectNode();
            result.put("content", "");
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current selection", e);
            ObjectNode result = objectNode();
            result.put("content", "");
            return result;
        }
    }
    
    private JsonNode handleCloseTab(String filePath) {
        try {
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject != null) {
                // Find the TopComponent for this file
                for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                    Node[] nodes = tc.getActivatedNodes();
                    if (nodes != null && nodes.length > 0) {
                        DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                        if (dataObject != null && dataObject.getPrimaryFile().equals(fileObject)) {
                            // Close the tab
                            tc.close();
                            
                            ObjectNode result = objectNode();
                            result.put("content", "Tab closed successfully: " + filePath);
                            return result;
                        }
                    }
                }
                
                // If tab not found in open tabs, still return success
                ObjectNode result = objectNode();
                result.put("content", "Tab not currently open: " + filePath);
                return result;
            }
            
            throw new IllegalArgumentException("File not found: " + filePath);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
    
    private JsonNode handleGetDiagnostics() {
        try {
            ObjectNode result = objectNode();
            ObjectNode diagnostics = objectNode();
            
            // NetBeans IDE information
            diagnostics.put("netbeans_version", System.getProperty("netbeans.buildnumber", "Unknown"));
            File nbUser = Places.getUserDirectory();
            diagnostics.put("netbeans_user", nbUser != null ? nbUser.getAbsolutePath() : "Unknown");
            
            // Java information
            diagnostics.put("java_version", System.getProperty("java.version"));
            diagnostics.put("java_vendor", System.getProperty("java.vendor"));
            diagnostics.put("java_home", System.getProperty("java.home"));
            
            // Operating system information
            diagnostics.put("os_name", System.getProperty("os.name"));
            diagnostics.put("os_version", System.getProperty("os.version"));
            diagnostics.put("os_arch", System.getProperty("os.arch"));
            
            // Plugin information
            diagnostics.put("plugin_version", "1.0.7");
            diagnostics.put("mcp_protocol_version", "2024-11-05");
            
            // Open projects count
            Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
            diagnostics.put("open_projects_count", openProjects.length);
            
            // Open editors count
            int openEditorsCount = 0;
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                Node[] nodes = tc.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        openEditorsCount++;
                    }
                }
            }
            diagnostics.put("open_editors_count", openEditorsCount);
            
            // Memory information
            Runtime runtime = Runtime.getRuntime();
            diagnostics.put("max_memory_mb", runtime.maxMemory() / (1024 * 1024));
            diagnostics.put("total_memory_mb", runtime.totalMemory() / (1024 * 1024));
            diagnostics.put("free_memory_mb", runtime.freeMemory() / (1024 * 1024));
            diagnostics.put("used_memory_mb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
            
            result.set("content", diagnostics);
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics", e);
            ObjectNode result = objectNode();
            result.put("isError", true);
            result.put("content", "Failed to get diagnostics: " + e.getMessage());
            return result;
        }
    }
    
    // Helper methods
    
    private void collectProjectFiles(FileObject dir, ArrayNode files, String relativePath) {
        for (FileObject child : dir.getChildren()) {
            String childPath = relativePath.isEmpty() ? child.getName() : relativePath + "/" + child.getName();
            
            ObjectNode fileInfo = objectMapper.createObjectNode();
            fileInfo.put("name", child.getName());
            fileInfo.put("path", childPath);
            fileInfo.put("isFolder", child.isFolder());
            files.add(fileInfo);
            
            if (child.isFolder()) {
                collectProjectFiles(child, files, childPath);
            }
        }
    }
    
    private JsonNode getProjectInfo(String projectPath) {
        FileObject projectDir = FileUtil.toFileObject(new File(projectPath));
        if (projectDir == null) {
            throw new IllegalArgumentException("Project not found: " + projectPath);
        }
        
        ObjectNode projectInfo = objectMapper.createObjectNode();
        projectInfo.put("path", projectPath);
        projectInfo.put("name", projectDir.getName());
        
        ArrayNode files = objectMapper.createArrayNode();
        collectProjectFiles(projectDir, files, "");
        projectInfo.set("files", files);
        
        return projectInfo;
    }
    
    private ObjectNode createTool(String name, String description, String... params) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();
        
        for (int i = 0; i < params.length; i += 3) {
            String paramName = params[i];
            String paramType = params[i + 1];
            String paramDesc = i + 2 < params.length ? params[i + 2] : "";
            
            ObjectNode param = objectMapper.createObjectNode();
            param.put("type", paramType);
            param.put("description", paramDesc);
            properties.set(paramName, param);
            required.add(paramName);
        }
        
        inputSchema.set("properties", properties);
        inputSchema.set("required", required);
        tool.set("inputSchema", inputSchema);
        
        return tool;
    }
    
    private ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }
    
    private String createErrorResponse(String id, int code, String message, String data) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.put("id", id);
            }
            
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }
            response.set("error", error);
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create error response", e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
    
    public void setWebSocketSession(Session session) {
        this.webSocketSession = session;
        
        if (session != null) {
            // Start tracking selection changes when connected
            startSelectionTracking();
        } else {
            // Stop tracking when disconnected
            stopSelectionTracking();
        }
    }
    
    /**
     * Security validation: Checks if the given file path is within any open project directory.
     * This prevents unauthorized file operations outside of the user's active workspace.
     * 
     * @param filePath the file path to validate
     * @return true if the path is within an open project, false otherwise
     */
    private boolean isPathWithinOpenProjects(String filePath) {
        try {
            File targetFile = new File(filePath).getCanonicalFile();
            String targetPath = targetFile.getAbsolutePath();
            
            // Get all open projects
            Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
            
            for (Project project : openProjects) {
                FileObject projectDir = project.getProjectDirectory();
                if (projectDir != null) {
                    File projectFile = new File(projectDir.getPath()).getCanonicalFile();
                    String projectPath = projectFile.getAbsolutePath();
                    
                    // Check if target path is within this project directory
                    if (targetPath.startsWith(projectPath + File.separator) || targetPath.equals(projectPath)) {
                        LOGGER.log(Level.FINE, "File path {0} is within project: {1}", 
                                  new Object[]{filePath, projectPath});
                        return true;
                    }
                }
            }
            
            LOGGER.log(Level.WARNING, "File path {0} is not within any open project directory", filePath);
            return false;
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error validating file path: " + filePath, e);
            return false; // Deny access on any path resolution errors
        }
    }
    
    /**
     * Starts tracking selection changes in editors.
     */
    private void startSelectionTracking() {
        // Listen for TopComponent activation changes
        topComponentListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (TopComponent.Registry.PROP_ACTIVATED.equals(evt.getPropertyName())) {
                    TopComponent activated = TopComponent.getRegistry().getActivated();
                    if (activated != null) {
                        trackEditorSelection(activated);
                    }
                }
            }
        };
        
        TopComponent.getRegistry().addPropertyChangeListener(topComponentListener);
        
        // Track the currently active editor if any
        TopComponent activated = TopComponent.getRegistry().getActivated();
        if (activated != null) {
            trackEditorSelection(activated);
        }
        
        LOGGER.log(Level.FINE, "Started selection tracking");
    }
    
    /**
     * Stops tracking selection changes.
     */
    private void stopSelectionTracking() {
        // Remove TopComponent listener
        if (topComponentListener != null) {
            TopComponent.getRegistry().removePropertyChangeListener(topComponentListener);
            topComponentListener = null;
        }
        
        // Remove all selection listeners
        for (Map.Entry<JTextComponent, CaretListener> entry : selectionListeners.entrySet()) {
            entry.getKey().removeCaretListener(entry.getValue());
        }
        selectionListeners.clear();
        currentTextComponent = null;
        
        LOGGER.log(Level.FINE, "Stopped selection tracking");
    }
    
    /**
     * Tracks selection changes in the given TopComponent if it's an editor.
     */
    private void trackEditorSelection(TopComponent tc) {
        try {
            Node[] nodes = tc.getActivatedNodes();
            if (nodes != null && nodes.length > 0) {
                EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
                if (editorCookie != null) {
                    JTextComponent[] panes = editorCookie.getOpenedPanes();
                    if (panes != null && panes.length > 0) {
                        JTextComponent textComponent = panes[0];
                        
                        // Only track if it's a different component
                        if (textComponent != currentTextComponent) {
                            // Remove listener from previous component
                            if (currentTextComponent != null) {
                                CaretListener listener = selectionListeners.remove(currentTextComponent);
                                if (listener != null) {
                                    currentTextComponent.removeCaretListener(listener);
                                }
                            }
                            
                            // Add listener to new component
                            currentTextComponent = textComponent;
                            CaretListener listener = new CaretListener() {
                                @Override
                                public void caretUpdate(CaretEvent e) {
                                    sendSelectionChangeEvent(textComponent, nodes[0]);
                                }
                            };
                            
                            textComponent.addCaretListener(listener);
                            selectionListeners.put(textComponent, listener);
                            
                            // Send initial selection event
                            sendSelectionChangeEvent(textComponent, nodes[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error tracking editor selection", e);
        }
    }
    
    /**
     * Sends a selection_changed event to Claude Code via WebSocket.
     */
    private void sendSelectionChangeEvent(JTextComponent textComponent, Node node) {
        try {
            if (webSocketSession == null || !webSocketSession.isOpen()) {
                return;
            }
            
            // Get selection details
            String selectedText = textComponent.getSelectedText();
            int selectionStart = textComponent.getSelectionStart();
            int selectionEnd = textComponent.getSelectionEnd();
            
            // Get document and file info
            Document doc = textComponent.getDocument();
            DataObject dataObject = node.getLookup().lookup(DataObject.class);
            
            if (doc instanceof StyledDocument && dataObject != null) {
                StyledDocument styledDoc = (StyledDocument) doc;
                FileObject fileObject = dataObject.getPrimaryFile();
                
                if (fileObject != null) {
                    // Get file path
                    File file = FileUtil.toFile(fileObject);
                    String absolutePath = file.getAbsolutePath();
                    String fileUrl = "file://" + absolutePath;
                    
                    // Calculate line and column positions (0-based for protocol)
                    int startLine = NbDocument.findLineNumber(styledDoc, selectionStart);
                    int startColumn = NbDocument.findLineColumn(styledDoc, selectionStart);
                    int endLine = NbDocument.findLineNumber(styledDoc, selectionEnd);
                    int endColumn = NbDocument.findLineColumn(styledDoc, selectionEnd);
                    
                    // Create selection_changed notification
                    ObjectNode notification = objectMapper.createObjectNode();
                    notification.put("jsonrpc", "2.0");
                    notification.put("method", "selection_changed");
                    
                    ObjectNode params = objectMapper.createObjectNode();
                    
                    // Add text (selected text or empty string)
                    params.put("text", selectedText != null ? selectedText : "");
                    
                    // Add file paths
                    params.put("filePath", absolutePath);
                    params.put("fileUrl", fileUrl);
                    
                    // Add selection object
                    ObjectNode selection = objectMapper.createObjectNode();
                    
                    ObjectNode start = objectMapper.createObjectNode();
                    start.put("line", startLine);
                    start.put("character", startColumn);
                    selection.set("start", start);
                    
                    ObjectNode end = objectMapper.createObjectNode();
                    end.put("line", endLine);
                    end.put("character", endColumn);
                    selection.set("end", end);
                    
                    // Set isEmpty based on whether there's selected text
                    selection.put("isEmpty", selectedText == null || selectedText.isEmpty());
                    
                    params.set("selection", selection);
                    notification.set("params", params);
                    
                    // Send the notification
                    String message = objectMapper.writeValueAsString(notification);
                    webSocketSession.getRemote().sendString(message);
                    
                    LOGGER.log(Level.FINE, "Sent selection_changed event: {0}", message);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error sending selection change event", e);
        }
    }
}
package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.netbeans.api.diff.Diff;
import org.netbeans.api.diff.DiffView;
import org.netbeans.api.diff.StreamSource;
import org.netbeans.api.diff.Difference;
import org.openide.util.Lookup;
import java.io.StringReader;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;

/**
 * Handles Model Context Protocol messages and provides NetBeans IDE capabilities
 * to Claude Code through MCP primitives (Tools, Resources, Prompts).
 */
public class NetBeansMCPHandler {
    
    /**
     * Data class to hold current text selection information from NetBeans.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectionData {
        public final String text;
        public final String filePath;
        public final int startLine;
        public final int startColumn;
        public final int endLine;
        public final int endColumn;
        public final boolean isEmpty;
        
        public SelectionData(String selectedText, String filePath, 
                           int startLine, int startColumn, 
                           int endLine, int endColumn) {
            this.text = selectedText != null ? selectedText : "";
            this.filePath = filePath;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.isEmpty = selectedText == null || selectedText.isEmpty();
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(NetBeansMCPHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MCPResponseBuilder responseBuilder;
    private Session webSocketSession;
    
    // Selection tracking
    private final Map<JTextComponent, CaretListener> selectionListeners = new WeakHashMap<>();
    private PropertyChangeListener topComponentListener;
    private JTextComponent currentTextComponent;
    
    public NetBeansMCPHandler() {
        this.responseBuilder = new MCPResponseBuilder(objectMapper);
    }
    
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
            Integer id = message.has("id") ? message.get("id").asInt() : null;
            
            LOGGER.log(Level.FINE, "Processing MCP method: {0}", method);
            
            ObjectNode response = responseBuilder.objectNode();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.put("id", id);
            }
            
            switch (method) {
                case "initialize":
                    response.set("result", handleInitialize(params));
                    // Send the response first
                    String initResponse = objectMapper.writeValueAsString(response);
                    // Then send notifications/initialized notification
                    sendInitializedNotification();
                    return initResponse;
                    
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
                    return responseBuilder.createErrorResponse(id, -32601, "Method not found", method);
            }
            
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling MCP message", e);
            return responseBuilder.createErrorResponse(null, -32603, "Internal error", e.getMessage());
        }
    }
    
    /**
     * Handles MCP initialize request.
     */
    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode result = responseBuilder.objectNode();
        result.put("protocolVersion", "2024-11-05");
        
        ObjectNode capabilities = responseBuilder.objectNode();
        
        ObjectNode toolsCapability = responseBuilder.objectNode();
        toolsCapability.put("listChanged", true);
        capabilities.set("tools", toolsCapability);
        
        ObjectNode resourcesCapability = responseBuilder.objectNode();
        resourcesCapability.put("subscribe", true);
        resourcesCapability.put("listChanged", true);
        capabilities.set("resources", resourcesCapability);
        
        ObjectNode promptsCapability = responseBuilder.objectNode();
        promptsCapability.put("listChanged", true);
        capabilities.set("prompts", promptsCapability);
        
        result.set("capabilities", capabilities);
        
        ObjectNode serverInfo = responseBuilder.objectNode();
        serverInfo.put("name", "netbeans-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        
        return result;
    }
    
    /**
     * Sends the notifications/initialized notification after successful initialization.
     */
    private void sendInitializedNotification() {
        try {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                ObjectNode notification = responseBuilder.createNotification(
                    "notifications/initialized", null
                );
                String message = objectMapper.writeValueAsString(notification);
                webSocketSession.getRemote().sendString(message);
                LOGGER.log(Level.FINE, "Sent notifications/initialized notification");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send initialized notification", e);
        }
    }
    
    /**
     * Lists available tools (executable functions).
     */
    private JsonNode handleToolsList() {
        ArrayNode tools = responseBuilder.arrayNode();
        
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
            "tab_name", "string", "Name of the tab to close"));
        
        tools.add(createTool("getDiagnostics", "Get diagnostics information about the IDE and environment"));
        
        tools.add(createTool("checkDocumentDirty", "Check if a document has unsaved changes",
            "filePath", "string", "Path to the file to check"));
            
        tools.add(createTool("saveDocument", "Save a document to disk",
            "filePath", "string", "Path to the file to save"));
            
        tools.add(createTool("closeAllDiffTabs", "Close all diff viewer tabs"));
        
        tools.add(createTool("openDiff", "Open a diff viewer comparing two files",
            "old_file_path", "string", "Path to the original file",
            "new_file_path", "string", "Path to the modified file",
            "new_file_contents", "string", "Contents of the modified file",
            "tab_name", "string", "Name for the diff tab (optional)"));
        
        ObjectNode result = responseBuilder.objectNode();
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
                    JsonNode closeTabNameNode = arguments.get("tab_name");
                    if (closeTabNameNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: tab_name");
                    }
                    return handleCloseTab(closeTabNameNode.asText());
                    
                case "getDiagnostics":
                    return handleGetDiagnostics();
                    
                case "checkDocumentDirty":
                    JsonNode dirtyPathNode = arguments.get("filePath");
                    if (dirtyPathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: filePath");
                    }
                    return handleCheckDocumentDirty(dirtyPathNode.asText());
                    
                case "saveDocument":
                    JsonNode savePathNode = arguments.get("filePath");
                    if (savePathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: filePath");
                    }
                    return handleSaveDocument(savePathNode.asText());
                    
                case "closeAllDiffTabs":
                    return handleCloseAllDiffTabs();
                    
                case "openDiff":
                    JsonNode oldFilePathNode = arguments.get("old_file_path");
                    JsonNode newFilePathNode = arguments.get("new_file_path");
                    JsonNode newFileContentsNode = arguments.get("new_file_contents");
                    if (oldFilePathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: old_file_path");
                    }
                    if (newFilePathNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: new_file_path");
                    }
                    if (newFileContentsNode == null) {
                        throw new IllegalArgumentException("Missing required parameter: new_file_contents");
                    }
                    String tabName = arguments.has("tab_name") ? arguments.get("tab_name").asText() : null;
                    return handleOpenDiff(oldFilePathNode.asText(), newFilePathNode.asText(), 
                                        newFileContentsNode.asText(), tabName);
                    
                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing tool: " + toolName, e);
            
            return responseBuilder.createToolResponse("Error: " + e.getMessage());
        }
    }
    
    /**
     * Lists available resources.
     */
    private JsonNode handleResourcesList() {
        ArrayNode resources = responseBuilder.arrayNode();
        
         Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
         for (Project project : openProjects) {
             ObjectNode resource = responseBuilder.objectNode();
             resource.put("uri", "project://" + project.getProjectDirectory().getPath());
             resource.put("name", ProjectUtils.getInformation(project).getDisplayName());
             resource.put("description", "NetBeans project: " + ProjectUtils.getInformation(project).getDisplayName());
             resource.put("mimeType", "application/json");
             resources.add(resource);
         }
        
        ObjectNode result = responseBuilder.objectNode();
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
        ArrayNode prompts = responseBuilder.arrayNode();
        
        ObjectNode codeReviewPrompt = responseBuilder.objectNode();
        codeReviewPrompt.put("name", "code_review");
        codeReviewPrompt.put("description", "Review code in NetBeans project");
        prompts.add(codeReviewPrompt);
        
        ObjectNode result = responseBuilder.objectNode();
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
        
        return responseBuilder.createToolResponse(content);
    }
    
    private JsonNode handleWriteFile(String filePath, String content) throws IOException {
        // Security check: Only allow writing to files within open project directories
        if (!isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File write denied: Path is not within any open project directory: " + filePath);
        }
        
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        
        return responseBuilder.createToolResponse("File written successfully: " + filePath);
    }
    
    private JsonNode handleListFiles(String dirPath) {
        // Security check: Only allow listing directories within open project directories
        if (!isPathWithinOpenProjects(dirPath)) {
            throw new SecurityException("Directory listing denied: Path is not within any open project directory: " + dirPath);
        }
        
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        
        ArrayNode fileList = responseBuilder.arrayNode();
        if (files != null) {
            for (File file : files) {
                ObjectNode fileInfo = responseBuilder.objectNode();
                fileInfo.put("name", file.getName());
                fileInfo.put("path", file.getAbsolutePath());
                fileInfo.put("isDirectory", file.isDirectory());
                fileInfo.put("size", file.length());
                fileList.add(fileInfo);
            }
        }
        
        return responseBuilder.createToolResponse(fileList);
    }
    
    private JsonNode handleGetOpenProjects() {
        ArrayNode projects = responseBuilder.arrayNode();
        
         Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
         for (Project project : openProjects) {
             ObjectNode projectInfo = responseBuilder.objectNode();
             projectInfo.put("name", ProjectUtils.getInformation(project).getDisplayName());
             projectInfo.put("path", project.getProjectDirectory().getPath());
             projects.add(projectInfo);
         }
        
        return responseBuilder.createToolResponse(projects);
    }
    
    private JsonNode handleGetProjectFiles(String projectPath) {
        FileObject projectDir = FileUtil.toFileObject(new File(projectPath));
        if (projectDir == null) {
            throw new IllegalArgumentException("Project not found: " + projectPath);
        }
        
        ArrayNode files = responseBuilder.arrayNode();
        collectProjectFiles(projectDir, files, "");
        
        return responseBuilder.createToolResponse(files);
    }
    
    private JsonNode handleGetOpenDocuments() {
        ArrayNode documents = responseBuilder.arrayNode();
        
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
                            ObjectNode docInfo = responseBuilder.objectNode();
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
        
        return responseBuilder.createToolResponse(documents);
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
                        return responseBuilder.createToolResponse(content);
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
                    
                    return responseBuilder.createToolResponse("File opened successfully: " + filePath);
                }
            }
            
            throw new RuntimeException("Failed to open file in editor");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to open file: " + e.getMessage(), e);
        }
    }
    
    private JsonNode handleGetWorkspaceFolders() {
        ArrayNode folders = responseBuilder.arrayNode();
        
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        for (Project project : openProjects) {
            ObjectNode folderInfo = responseBuilder.objectNode();
            folderInfo.put("name", ProjectUtils.getInformation(project).getDisplayName());
            folderInfo.put("uri", "file://" + project.getProjectDirectory().getPath());
            folders.add(folderInfo);
        }
        
        return responseBuilder.createToolResponse(folders);
    }
    
    private JsonNode handleGetOpenEditors() {
        return handleGetOpenDocuments(); // Reuse existing implementation
    }
    
    private JsonNode handleGetCurrentSelection() {
        try {
            SelectionData selectionData = getCurrentSelectionData();
            if (selectionData != null) {
                return buildSelectionResponse(selectionData);
            } else {
                return responseBuilder.createToolResponse("");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current selection", e);
            return responseBuilder.createToolResponse("");
        }
    }
    
    /**
     * Extracts current text selection data from NetBeans IDE.
     * 
     * @return SelectionData object with selection information, or null if no selection
     */
    private SelectionData getCurrentSelectionData() {
        // Get the current active editor
        TopComponent activeTC = TopComponent.getRegistry().getActivated();
        if (activeTC == null) {
            return null;
        }
        
        Node[] nodes = activeTC.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }
        
        EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
        if (editorCookie == null) {
            return null;
        }
        
        // Get the opened editor panes
        JTextComponent[] panes = editorCookie.getOpenedPanes();
        if (panes == null || panes.length == 0) {
            return null;
        }
        
        // Use the first pane (usually the active one)
        JTextComponent textComponent = panes[0];
        if (textComponent == null || !(textComponent.getDocument() instanceof StyledDocument)) {
            return null;
        }
        
        StyledDocument doc = (StyledDocument) textComponent.getDocument();
        String selectedText = textComponent.getSelectedText();
        int selectionStart = textComponent.getSelectionStart();
        int selectionEnd = textComponent.getSelectionEnd();
        
        // Get file path
        String filePath = null;
        DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
        if (dataObject != null) {
            FileObject fileObject = dataObject.getPrimaryFile();
            if (fileObject != null) {
                filePath = fileObject.getPath();
            }
        }
        
        // Use NbDocument utility methods for line/column calculation
        int startLine = NbDocument.findLineNumber(doc, selectionStart) + 1; // Convert to 1-based
        int startColumn = NbDocument.findLineColumn(doc, selectionStart);
        int endLine = NbDocument.findLineNumber(doc, selectionEnd) + 1; // Convert to 1-based
        int endColumn = NbDocument.findLineColumn(doc, selectionEnd);
        
        return new SelectionData(selectedText, filePath, startLine, startColumn, endLine, endColumn);
    }
    
    /**
     * Builds JSON response for selection data.
     * 
     * @param selectionData the selection data to convert to JSON response
     * @return JSON response for the selection
     */
    private JsonNode buildSelectionResponse(SelectionData selectionData) {
        if (selectionData.isEmpty) {
            return responseBuilder.createToolResponse("");
        }
        
        // Let Jackson automatically serialize the SelectionData object
        return responseBuilder.createToolResponse(selectionData);
    }
    
    private JsonNode handleCloseTab(String tabName) {
        try {
            // Find the TopComponent by tab name
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                if (tc.getDisplayName().equals(tabName)) {
                    // Close the tab
                    tc.close();
                    return responseBuilder.createToolResponse("Tab closed successfully: " + tabName);
                }
            }
            
            // If no exact match found, try to find by file name (without path)
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                Node[] nodes = tc.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        String fileName = dataObject.getPrimaryFile().getName();
                        if (fileName.equals(tabName) || (fileName + "." + dataObject.getPrimaryFile().getExt()).equals(tabName)) {
                            tc.close();
                            return responseBuilder.createToolResponse("Tab closed successfully: " + tabName);
                        }
                    }
                }
            }
            
            // If tab not found in open tabs
            return responseBuilder.createToolResponse("Tab not currently open: " + tabName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
    
    private JsonNode handleGetDiagnostics() {
        try {
            ObjectNode diagnostics = responseBuilder.objectNode();
            
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
            
            return responseBuilder.createToolResponse(diagnostics);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics", e);
            return responseBuilder.createToolResponse("Failed to get diagnostics: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a document has unsaved changes.
     */
    private JsonNode handleCheckDocumentDirty(String filePath) {
        try {
            // Security check: Only allow checking files within open project directories
            if (!isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File access denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject != null) {
                try {
                    DataObject dataObject = DataObject.find(fileObject);
                    if (dataObject != null) {
                        boolean isDirty = dataObject.isModified();
                        
                        ObjectNode result = responseBuilder.objectNode();
                        result.put("filePath", filePath);
                        result.put("isDirty", isDirty);
                        
                        // Also check if the file is currently open in an editor
                        EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                        boolean isOpen = editorCookie != null && editorCookie.getOpenedPanes() != null && editorCookie.getOpenedPanes().length > 0;
                        result.put("isOpen", isOpen);
                        
                        return responseBuilder.createToolResponse(result);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error checking DataObject for file: " + filePath, e);
                }
            }
            
            // File not found or not a NetBeans-managed file
            ObjectNode result = responseBuilder.objectNode();
            result.put("filePath", filePath);
            result.put("isDirty", false);
            result.put("isOpen", false);
            result.put("note", "File not found or not currently managed by NetBeans");
            
            return responseBuilder.createToolResponse(result);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking document dirty state: " + filePath, e);
            return responseBuilder.createToolResponse("Error checking document dirty state: " + e.getMessage());
        }
    }
    
    /**
     * Saves a document to disk.
     */
    private JsonNode handleSaveDocument(String filePath) {
        try {
            // Security check: Only allow saving files within open project directories
            if (!isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File save denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject != null) {
                try {
                    DataObject dataObject = DataObject.find(fileObject);
                    if (dataObject != null) {
                        EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                        
                        if (editorCookie != null) {
                            // Save the document
                            editorCookie.saveDocument();
                            
                            ObjectNode result = responseBuilder.objectNode();
                            result.put("filePath", filePath);
                            result.put("saved", true);
                            result.put("message", "Document saved successfully");
                            
                            return responseBuilder.createToolResponse(result);
                        } else {
                            return responseBuilder.createToolResponse("File is not editable or not currently managed by an editor");
                        }
                    } else {
                        return responseBuilder.createToolResponse("File is not managed by NetBeans");
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error saving document: " + filePath, e);
                    return responseBuilder.createToolResponse("Error saving document: " + e.getMessage());
                }
            } else {
                return responseBuilder.createToolResponse("File not found: " + filePath);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error in saveDocument: " + filePath, e);
            return responseBuilder.createToolResponse("Error saving document: " + e.getMessage());
        }
    }
    
    /**
     * Closes all diff viewer tabs.
     */
    private JsonNode handleCloseAllDiffTabs() {
        try {
            int closedCount = 0;
            
            // Get all open TopComponents and look for diff viewers
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                String displayName = tc.getDisplayName();
                String name = tc.getName();
                
                // Look for components that might be diff viewers
                // NetBeans diff viewers typically have names containing "diff" or similar patterns
                if (displayName != null && (displayName.toLowerCase().contains("diff") || 
                    displayName.contains(" vs ") || displayName.contains(" - "))) {
                    try {
                        tc.close();
                        closedCount++;
                        LOGGER.log(Level.FINE, "Closed diff tab: {0}", displayName);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to close diff tab: " + displayName, e);
                    }
                }
                
                // Also check by class name for known diff viewer classes
                String className = tc.getClass().getSimpleName();
                if (className.toLowerCase().contains("diff") || 
                    className.toLowerCase().contains("compare")) {
                    try {
                        tc.close();
                        closedCount++;
                        LOGGER.log(Level.FINE, "Closed diff component: {0}", className);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to close diff component: " + className, e);
                    }
                }
            }
            
            ObjectNode result = responseBuilder.objectNode();
            result.put("closedCount", closedCount);
            result.put("message", "Closed " + closedCount + " diff viewer tabs");
            
            return responseBuilder.createToolResponse(result);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing diff tabs", e);
            return responseBuilder.createToolResponse("Error closing diff tabs: " + e.getMessage());
        }
    }
    
    /**
     * Opens a diff viewer comparing two files.
     */
    private JsonNode handleOpenDiff(String oldFilePath, String newFilePath, 
                                   String newFileContents, String tabName) {
        try {
            // Security check: Only allow diffing files within open project directories
            if (!isPathWithinOpenProjects(oldFilePath)) {
                throw new SecurityException("File access denied: old_file_path is not within any open project directory: " + oldFilePath);
            }
            if (!isPathWithinOpenProjects(newFilePath)) {
                throw new SecurityException("File access denied: new_file_path is not within any open project directory: " + newFilePath);
            }
            
            // Read the old file content
            File oldFile = new File(oldFilePath);
            if (!oldFile.exists()) {
                return responseBuilder.createToolResponse("Old file does not exist: " + oldFilePath);
            }
            
            String oldFileContents;
            try {
                oldFileContents = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return responseBuilder.createToolResponse("Failed to read old file: " + e.getMessage());
            }
            
            // Create stream sources for diff
            StreamSource oldSource = new StreamSource() {
                @Override
                public String getName() {
                    return oldFile.getName() + " (original)";
                }
                
                @Override
                public String getTitle() {
                    return oldFilePath;
                }
                
                @Override
                public String getMIMEType() {
                    return "text/plain";
                }
                
                @Override
                public Reader createReader() throws IOException {
                    return new StringReader(oldFileContents);
                }
                
                @Override
                public Writer createWriter(Difference[] conflicts) throws IOException {
                    throw new IOException("Writing not supported for original file");
                }
            };
            
            StreamSource newSource = new StreamSource() {
                @Override
                public String getName() {
                    return new File(newFilePath).getName() + " (modified)";
                }
                
                @Override
                public String getTitle() {
                    return newFilePath;
                }
                
                @Override
                public String getMIMEType() {
                    return "text/plain";
                }
                
                @Override
                public Reader createReader() throws IOException {
                    return new StringReader(newFileContents);
                }
                
                @Override
                public Writer createWriter(Difference[] conflicts) throws IOException {
                    throw new IOException("Writing not supported for modified content");
                }
            };
            
            // Get Diff service and create diff view
            Diff diffService = Lookup.getDefault().lookup(Diff.class);
            if (diffService != null) {
                try {
                    String diffTabName = tabName != null ? tabName : 
                        "Diff: " + oldFile.getName() + " vs " + new File(newFilePath).getName();
                        
                    DiffView diffView = diffService.createDiff(oldSource, newSource);
                    if (diffView != null) {
                        // Open the diff view
                        java.awt.Component component = diffView.getComponent();
                        if (component instanceof TopComponent) {
                            TopComponent diffTC = (TopComponent) component;
                            diffTC.setDisplayName(diffTabName);
                            diffTC.open();
                            diffTC.requestActive();
                        } else {
                            // If it's not a TopComponent, try to show it in another way
                            LOGGER.log(Level.WARNING, "Diff component is not a TopComponent: {0}", component.getClass());
                        }
                        
                        ObjectNode result = responseBuilder.objectNode();
                        result.put("success", true);
                        result.put("tabName", diffTabName);
                        result.put("oldFile", oldFilePath);
                        result.put("newFile", newFilePath);
                        result.put("message", "Diff viewer opened successfully");
                        
                        return responseBuilder.createToolResponse(result);
                    } else {
                        return responseBuilder.createToolResponse("Failed to create diff view");
                    }
                } catch (IOException e) {
                    return responseBuilder.createToolResponse("Error creating diff: " + e.getMessage());
                }
            } else {
                return responseBuilder.createToolResponse("Diff service not available");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error opening diff", e);
            return responseBuilder.createToolResponse("Error opening diff: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private void collectProjectFiles(FileObject dir, ArrayNode files, String relativePath) {
        for (FileObject child : dir.getChildren()) {
            String childPath = relativePath.isEmpty() ? child.getName() : relativePath + "/" + child.getName();
            
            ObjectNode fileInfo = responseBuilder.objectNode();
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
        
        ObjectNode projectInfo = responseBuilder.objectNode();
        projectInfo.put("path", projectPath);
        projectInfo.put("name", projectDir.getName());
        
        ArrayNode files = responseBuilder.arrayNode();
        collectProjectFiles(projectDir, files, "");
        projectInfo.set("files", files);
        
        return projectInfo;
    }
    
    private ObjectNode createTool(String name, String description, String... params) {
        ObjectNode tool = responseBuilder.objectNode();
        tool.put("name", name);
        tool.put("description", description);
        
        ObjectNode inputSchema = responseBuilder.objectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = responseBuilder.objectNode();
        ArrayNode required = responseBuilder.arrayNode();
        
        for (int i = 0; i < params.length; i += 3) {
            String paramName = params[i];
            String paramType = params[i + 1];
            String paramDesc = i + 2 < params.length ? params[i + 2] : "";
            
            ObjectNode param = responseBuilder.objectNode();
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
        return responseBuilder.objectNode();
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
                    ObjectNode params = responseBuilder.objectNode();
                    
                    // Add text (selected text or empty string)
                    params.put("text", selectedText != null ? selectedText : "");
                    
                    // Add file paths
                    params.put("filePath", absolutePath);
                    params.put("fileUrl", fileUrl);
                    
                    // Add selection object
                    ObjectNode selection = responseBuilder.objectNode();
                    
                    ObjectNode start = responseBuilder.objectNode();
                    start.put("line", startLine);
                    start.put("character", startColumn);
                    selection.set("start", start);
                    
                    ObjectNode end = responseBuilder.objectNode();
                    end.put("line", endLine);
                    end.put("character", endColumn);
                    selection.set("end", end);
                    
                    // Set isEmpty based on whether there's selected text
                    selection.put("isEmpty", selectedText == null || selectedText.isEmpty());
                    
                    params.set("selection", selection);
                    
                    // Create and send the notification
                    ObjectNode notification = responseBuilder.createNotification("selection_changed", params);
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
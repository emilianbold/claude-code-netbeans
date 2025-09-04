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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.diff.Diff;
import org.netbeans.api.diff.DiffView;
import org.netbeans.api.diff.StreamSource;
import org.netbeans.api.diff.Difference;
import org.openide.util.Lookup;
import java.io.StringReader;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import org.netbeans.editor.Annotations;
import org.netbeans.editor.AnnotationDesc;
import org.openbeans.claude.netbeans.tools.CloseAllDiffTabs;
import org.openbeans.claude.netbeans.tools.params.*;
import org.openide.loaders.DataObjectNotFoundException;

/**
 * Handles Model Context Protocol messages and provides NetBeans IDE capabilities
 * to Claude Code through MCP primitives (Tools, Resources, Prompts).
 */
public class NetBeansMCPHandler {

    /**
     * Data class to hold diagnostic information (errors, warnings) from NetBeans.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiagnosticData {
        public final String message;
        public final String severity; // "error", "warning", "info", "hint"
        public final String source;
        public final String filePath;
        public final int line;
        public final int column;
        public final String code; // error/warning code if available
        
        public DiagnosticData(String message, String severity, String source, 
                            String filePath, int line, int column, String code) {
            this.message = message;
            this.severity = severity;
            this.source = source;
            this.filePath = filePath;
            this.line = line;
            this.column = column;
            this.code = code;
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
    
    private final CloseAllDiffTabs closeAllDiffTabsTool;

    public NetBeansMCPHandler() {
        this.responseBuilder = new MCPResponseBuilder(objectMapper);
        this.closeAllDiffTabsTool = new CloseAllDiffTabs();
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
        
        // Core Claude Code tools - names/descriptions in code, schemas from JSON
        tools.add(createToolDefinition("openFile", "Opens a file in the editor", "OpenFileParams"));
        tools.add(createToolDefinition("getWorkspaceFolders", "Get list of workspace folders (open projects)", "getWorkspaceFolders"));
        tools.add(createToolDefinition("getOpenEditors", "Get list of currently open editor tabs", "getOpenEditors"));
        tools.add(createToolDefinition("getCurrentSelection", "Get the current text selection in the active editor", "getCurrentSelection"));
        tools.add(createToolDefinition("close_tab", "Close an open editor tab", "CloseTabParams"));
        tools.add(createToolDefinition("getDiagnostics", "Get diagnostic information (errors, warnings) for files", "GetDiagnosticsParams"));
        tools.add(createToolDefinition("checkDocumentDirty", "Check if a document has unsaved changes", "CheckDocumentDirtyParams"));
        tools.add(createToolDefinition("saveDocument", "Save a document to disk", "SaveDocumentParams"));
        tools.add(createToolDefinition("closeAllDiffTabs", "Close all diff viewer tabs", "closeAllDiffTabs"));
        tools.add(createToolDefinition("openDiff", "Open a git diff for the file", "OpenDiffParams"));
        
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
            ObjectMapper mapper = new ObjectMapper();
            
            switch (toolName) {
                // Core Claude Code tools
                case "openFile":
                    OpenFileParams openFileParams = mapper.convertValue(arguments, OpenFileParams.class);
                    return handleOpenFile(openFileParams.getFilePath(), 
                                        openFileParams.getPreview() != null ? openFileParams.getPreview() : false);
                    
                case "getWorkspaceFolders":
                    return handleGetWorkspaceFolders();
                    
                case "getOpenEditors":
                    return handleGetOpenEditors();
                    
                case "getCurrentSelection":
                    return handleGetCurrentSelection();
                    
                case "close_tab":
                    CloseTabParams closeTabParams = mapper.convertValue(arguments, CloseTabParams.class);
                    return handleCloseTab(closeTabParams.getTabName());
                    
                case "getDiagnostics":
                    GetDiagnosticsParams diagnosticsParams = mapper.convertValue(arguments, GetDiagnosticsParams.class);
                    return handleGetDiagnostics(diagnosticsParams.getUri());
                    
                case "checkDocumentDirty":
                    CheckDocumentDirtyParams dirtyParams = mapper.convertValue(arguments, CheckDocumentDirtyParams.class);
                    return handleCheckDocumentDirty(dirtyParams.getFilePath());
                    
                case "saveDocument":
                    SaveDocumentParams saveParams = mapper.convertValue(arguments, SaveDocumentParams.class);
                    return handleSaveDocument(saveParams.getFilePath());
                    
                case "closeAllDiffTabs":
                    return handleCloseAllDiffTabs();
                    
                case "openDiff":
                    OpenDiffParams diffParams = mapper.convertValue(arguments, OpenDiffParams.class);
                    return handleOpenDiff(
                        diffParams.getOldFilePath(), 
                        diffParams.getNewFilePath(), 
                        diffParams.getNewFileContents(), 
                        diffParams.getTabName());
                    
                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing tool: " + toolName, e);
            
            return responseBuilder.createToolResponse("Error: " + e.getMessage());
        }
    }
    
    /**
     * Data class to hold project information.
     */
    private static class ProjectData {
        final String path;
        final String displayName;
        
        ProjectData(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }
    }
    
    /**
     * Retrieves project data from NetBeans Platform.
     */
    private List<ProjectData> getOpenProjectsData() {
        List<ProjectData> projectDataList = new ArrayList<>();
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        
        for (Project project : openProjects) {
            String path = project.getProjectDirectory().getPath();
            String displayName = ProjectUtils.getInformation(project).getDisplayName();
            projectDataList.add(new ProjectData(path, displayName));
        }
        
        return projectDataList;
    }
    
    /**
     * Lists available resources.
     */
    private JsonNode handleResourcesList() {
        ArrayNode resources = responseBuilder.arrayNode();
        
        // Get project data from NetBeans
        List<ProjectData> projectDataList = getOpenProjectsData();
        
        // Build MCP response from the data
        for (ProjectData projectData : projectDataList) {
            ObjectNode resource = responseBuilder.objectNode();
            resource.put("uri", "project://" + projectData.path);
            resource.put("name", projectData.displayName);
            resource.put("description", "NetBeans project: " + projectData.displayName);
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
    
    private JsonNode handleGetOpenProjects() {
        ArrayNode projects = responseBuilder.arrayNode();
        
        // Get project data from NetBeans
        List<ProjectData> projectDataList = getOpenProjectsData();
        
        // Build MCP response from the data
        for (ProjectData projectData : projectDataList) {
            ObjectNode projectInfo = responseBuilder.objectNode();
            projectInfo.put("name", projectData.displayName);
            projectInfo.put("path", projectData.path);
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
        
        // Get project data using existing method
        List<ProjectData> projectDataList = getOpenProjectsData();
        
        // Build MCP response from the data
        for (ProjectData projectData : projectDataList) {
            ObjectNode folderInfo = responseBuilder.objectNode();
            folderInfo.put("name", projectData.displayName);
            folderInfo.put("uri", "file://" + projectData.path);
            folders.add(folderInfo);
        }
        
        return responseBuilder.createToolResponse(folders);
    }
    
    private JsonNode handleGetOpenEditors() {
        return handleGetOpenDocuments(); // Reuse existing implementation
    }
    
    private JsonNode handleGetCurrentSelection() {
        try {
            NbUtils.SelectionData selectionData = NbUtils.getCurrentSelectionData();
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
     * Builds JSON response for selection data.
     * 
     * @param selectionData the selection data to convert to JSON response
     * @return JSON response for the selection
     */
    private JsonNode buildSelectionResponse(NbUtils.SelectionData selectionData) {
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
                String displayName = tc.getDisplayName();
                if (displayName != null && displayName.equals(tabName)) {
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
            LOGGER.warning("Tab not found for close request: '" + tabName + "'");
            return responseBuilder.createToolResponse("Tab not currently open: " + tabName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
    
    private JsonNode handleGetDiagnostics(String uri) {
        try {
            if (uri != null) {
                // Get diagnostics for a specific file
                return getDiagnosticsForFile(uri);
            } else {
                // Get diagnostics for all open files
                return getDiagnosticsForAllFiles();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics", e);
            return responseBuilder.createToolResponse("Failed to get diagnostics: " + e.getMessage());
        }
    }
    
    /**
     * Extracts diagnostic information from NetBeans annotations for a specific file.
     */
    private JsonNode getDiagnosticsForFile(String uri) {
        try {
            // Convert URI to file path
            String filePath = uri.startsWith("file://") ? uri.substring(7) : uri;
            
            // Security check: Only allow files within open projects
            if (!isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File access denied: Path is not within any open project directory: " + filePath);
            }
            
            List<DiagnosticData> diagnostics = extractDiagnosticsFromFile(filePath);
            return responseBuilder.createToolResponse(diagnostics);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics for file: " + uri, e);
            return responseBuilder.createToolResponse("Failed to get diagnostics for file: " + e.getMessage());
        }
    }
    
    /**
     * Gets diagnostics for all currently open files.
     */
    private JsonNode getDiagnosticsForAllFiles() {
        try {
            List<DiagnosticData> allDiagnostics = new ArrayList<>();
            
            // Go through all open TopComponents (editor tabs)
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                Node[] nodes = tc.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        FileObject fileObject = dataObject.getPrimaryFile();
                        if (fileObject != null) {
                            File file = FileUtil.toFile(fileObject);
                            if (file != null) {
                                List<DiagnosticData> fileDiagnostics = extractDiagnosticsFromFile(file.getAbsolutePath());
                                allDiagnostics.addAll(fileDiagnostics);
                            }
                        }
                    }
                }
            }
            
            return responseBuilder.createToolResponse(allDiagnostics);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics for all files", e);
            return responseBuilder.createToolResponse("Failed to get diagnostics: " + e.getMessage());
        }
    }
    
    /**
     * Extracts diagnostic information from a specific file using NetBeans editor annotations.
     */
    private List<DiagnosticData> extractDiagnosticsFromFile(String filePath) {
        List<DiagnosticData> diagnostics = new ArrayList<>();
        
        try {
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject != null) {
                DataObject dataObject = DataObject.find(fileObject);
                if (dataObject != null) {
                    EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                    
                    if (editorCookie != null) {
                        Document doc = editorCookie.getDocument();
                        if (doc != null) {
                            // Get the annotations for this document
                            Annotations annotations = (Annotations) doc.getProperty(Annotations.class);
                            
                            if (annotations != null) {
                                // Iterate through lines with annotations using NetBeans API
                                int currentLine = annotations.getNextLineWithAnnotation(-1); // Start from the beginning
                                
                                while (currentLine != -1) {
                                    // Get the active annotation for this line
                                    AnnotationDesc activeAnnotation = annotations.getActiveAnnotation(currentLine);
                                    if (activeAnnotation != null) {
                                        DiagnosticData diagnostic = convertAnnotationToDiagnostic(
                                            activeAnnotation, filePath, currentLine + 1, 0);
                                        if (diagnostic != null) {
                                            diagnostics.add(diagnostic);
                                        }
                                    }
                                    
                                    // Get all passive annotations for this line
                                    AnnotationDesc[] passiveAnnotations = annotations.getPassiveAnnotationsForLine(currentLine);
                                    if (passiveAnnotations != null) {
                                        for (AnnotationDesc annotation : passiveAnnotations) {
                                            DiagnosticData diagnostic = convertAnnotationToDiagnostic(
                                                annotation, filePath, currentLine + 1, 0);
                                            if (diagnostic != null) {
                                                diagnostics.add(diagnostic);
                                            }
                                        }
                                    }
                                    
                                    // Move to the next line with annotations
                                    currentLine = annotations.getNextLineWithAnnotation(currentLine);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not extract diagnostics from file: " + filePath, e);
        }
        
        return diagnostics;
    }
    
    /**
     * Converts a NetBeans AnnotationDesc to a DiagnosticData object.
     */
    private DiagnosticData convertAnnotationToDiagnostic(AnnotationDesc annotation, String filePath, 
                                                       int lineNumber, int columnNumber) {
        if (annotation == null) {
            return null;
        }
        
        String message = annotation.getShortDescription();
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        
        // Try to determine severity from annotation type
        String severity = "info"; // default
        String annotationType = annotation.getAnnotationType();
        String source = "netbeans";
        
        if (annotationType != null) {
            String lowerType = annotationType.toLowerCase();
            if (lowerType.contains("error")) {
                severity = "error";
                source = "compiler";
            } else if (lowerType.contains("warning") || lowerType.contains("warn")) {
                severity = "warning";
                source = "compiler";
            } else if (lowerType.contains("hint")) {
                severity = "hint";
                source = "editor";
            }
        }
        
        // Try to extract error code if available
        String code = null;
        if (message.contains("[") && message.contains("]")) {
            int start = message.lastIndexOf("[");
            int end = message.lastIndexOf("]");
            if (start < end && start >= 0) {
                code = message.substring(start + 1, end);
            }
        }
        
        return new DiagnosticData(message, severity, source, filePath, lineNumber, columnNumber, code);
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
    private JsonNode handleSaveDocument(String filePath) throws FileNotFoundException, DataObjectNotFoundException, IOException {
            // Security check: Only allow saving files within open project directories
            if (!isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File save denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject == null) {
                throw new FileNotFoundException(filePath);
            }
            DataObject dataObject = DataObject.find(fileObject);
            if (dataObject == null) {
                // not really possible as find would throw DataObjectNotFoundException
                throw new NullPointerException("dataObject");
            }
            EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);

            if (editorCookie == null) {
                throw new IllegalStateException("File is not editable or not currently managed by an editor");
            }
            // Save the document
            editorCookie.saveDocument();

            ObjectNode result = responseBuilder.objectNode();
            result.put("filePath", filePath);
            result.put("saved", true);
            result.put("message", "Document saved successfully");

            return responseBuilder.createToolResponse(result);
    }
    
    /**
     * Closes all diff viewer tabs.
     */
    private JsonNode handleCloseAllDiffTabs() {
        return this.closeAllDiffTabsTool.run(responseBuilder);
    }
    
    /**
     * Opens a diff viewer comparing two files.
     * If file paths are not provided, uses the current active editor.
     */
    private JsonNode handleOpenDiff(String oldFilePath, String newFilePath, 
                                   String newFileContents, String tabName) {
        try {
            // Track if we're using the current editor for defaults
            boolean usingCurrentEditor = false;
            String currentEditorPath = null;
            String currentEditorContent = null;
            
            // If paths are not provided, use current active editor
            String finalOldFilePath;
            String finalNewFilePath;
            if (oldFilePath == null || newFilePath == null) {
                currentEditorPath = getCurrentEditorFilePath();
                if (currentEditorPath == null) {
                    return responseBuilder.createToolResponse("No active editor found. Please specify file paths or open a file in the editor.");
                }
                usingCurrentEditor = true;
                currentEditorContent = getCurrentEditorContent(); // Get the buffer content with unsaved changes
                finalOldFilePath = (oldFilePath == null) ? currentEditorPath : oldFilePath;
                finalNewFilePath = (newFilePath == null) ? currentEditorPath : newFilePath;
            } else {
                finalOldFilePath = oldFilePath;
                finalNewFilePath = newFilePath;
            }
            
            // Security check: Only allow diffing files within open project directories
            if (!isPathWithinOpenProjects(finalOldFilePath)) {
                throw new SecurityException("File access denied: old_file_path is not within any open project directory: " + finalOldFilePath);
            }
            if (!isPathWithinOpenProjects(finalNewFilePath)) {
                throw new SecurityException("File access denied: new_file_path is not within any open project directory: " + finalNewFilePath);
            }
            
            // Read the old file content
            final File oldFile = new File(finalOldFilePath);
            final String oldFileContents;
            
            // If old file is the current editor and we're using it as default, use the editor buffer content
            if (usingCurrentEditor && oldFilePath == null && finalOldFilePath.equals(currentEditorPath) && currentEditorContent != null) {
                oldFileContents = currentEditorContent;
            } else {
                // Otherwise read from disk
                if (!oldFile.exists()) {
                    LOGGER.warning("Old file does not exist for diff: " + finalOldFilePath);
                    return responseBuilder.createToolResponse("Old file does not exist: " + finalOldFilePath);
                }
                try {
                    oldFileContents = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read old file for diff: " + finalOldFilePath, e);
                    return responseBuilder.createToolResponse("Failed to read old file: " + e.getMessage());
                }
            }
            
            // If new file contents not provided, determine how to get them
            final String finalNewFileContents;
            if (newFileContents == null) {
                // If new file is the current editor and we're using it as default, use the editor buffer content
                if (usingCurrentEditor && newFilePath == null && finalNewFilePath.equals(currentEditorPath) && currentEditorContent != null) {
                    finalNewFileContents = currentEditorContent;
                } else {
                    // Otherwise read from disk
                    File newFile = new File(finalNewFilePath);
                    if (!newFile.exists()) {
                        LOGGER.warning("New file does not exist for diff: " + finalNewFilePath);
                        return responseBuilder.createToolResponse("New file does not exist: " + finalNewFilePath);
                    }
                    try {
                        finalNewFileContents = Files.readString(newFile.toPath(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to read new file for diff: " + finalNewFilePath, e);
                        return responseBuilder.createToolResponse("Failed to read new file: " + e.getMessage());
                    }
                }
            } else {
                finalNewFileContents = newFileContents;
            }
            
            // Create stream sources for diff
            StreamSource oldSource = new StreamSource() {
                @Override
                public String getName() {
                    return oldFile.getName() + " (original)";
                }
                
                @Override
                public String getTitle() {
                    return finalOldFilePath;
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
                    return new File(finalNewFilePath).getName() + " (modified)";
                }
                
                @Override
                public String getTitle() {
                    return finalNewFilePath;
                }
                
                @Override
                public String getMIMEType() {
                    return "text/plain";
                }
                
                @Override
                public Reader createReader() throws IOException {
                    return new StringReader(finalNewFileContents);
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
                        "Diff: " + oldFile.getName() + " vs " + new File(finalNewFilePath).getName();
                        
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
                        result.put("oldFile", finalOldFilePath);
                        result.put("newFile", finalNewFilePath);
                        result.put("message", "Diff viewer opened successfully");
                        
                        return responseBuilder.createToolResponse(result);
                    } else {
                        LOGGER.warning("Failed to create diff view - diffView is null");
                        return responseBuilder.createToolResponse("Failed to create diff view");
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error creating diff view", e);
                    return responseBuilder.createToolResponse("Error creating diff: " + e.getMessage());
                }
            } else {
                LOGGER.warning("Diff service not available - no Diff implementation found in Lookup");
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
    
    private ObjectNode createToolDefinition(String toolName, String description, String schemaFileName) {
        ObjectNode tool = responseBuilder.objectNode();
        tool.put("name", toolName);
        tool.put("description", description);
        
        try {
            // Load parameter schema from JSON file
            String schemaPath = "/org/openbeans/claude/netbeans/tools/schemas/" + schemaFileName + ".json";
            InputStream inputStream = getClass().getResourceAsStream(schemaPath);
            
            if (inputStream == null) {
                // Fall back to empty schema if file not found
                LOGGER.warning("Schema file not found: " + schemaPath);
                ObjectNode inputSchema = responseBuilder.objectNode();
                inputSchema.put("type", "object");
                inputSchema.set("properties", responseBuilder.objectNode());
                inputSchema.set("required", responseBuilder.arrayNode());
                tool.set("inputSchema", inputSchema);
            } else {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode schema = mapper.readTree(inputStream);
                inputStream.close();
                
                // Set the loaded schema as inputSchema
                tool.set("inputSchema", schema);
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading parameter schema for: " + toolName, e);
            // Return minimal schema as fallback
            ObjectNode inputSchema = responseBuilder.objectNode();
            inputSchema.put("type", "object");
            inputSchema.set("properties", responseBuilder.objectNode());
            inputSchema.set("required", responseBuilder.arrayNode());
            tool.set("inputSchema", inputSchema);
        }
        
        return tool;
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
     * Gets the file path of the currently active editor.
     * 
     * @return the absolute path of the file in the active editor, or null if no editor is active
     */
    private String getCurrentEditorFilePath() {
        try {
            TopComponent activated = TopComponent.getRegistry().getActivated();
            if (activated != null) {
                Node[] nodes = activated.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        FileObject fileObject = dataObject.getPrimaryFile();
                        if (fileObject != null) {
                            File file = FileUtil.toFile(fileObject);
                            if (file != null) {
                                return file.getAbsolutePath();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current editor file path", e);
        }
        return null;
    }
    
    /**
     * Gets the content of the currently active editor from its document buffer.
     * This includes any unsaved changes.
     * 
     * @return the content of the active editor, or null if no editor is active
     */
    private String getCurrentEditorContent() {
        try {
            TopComponent activated = TopComponent.getRegistry().getActivated();
            if (activated != null) {
                Node[] nodes = activated.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
                    if (editorCookie != null) {
                        Document doc = editorCookie.getDocument();
                        if (doc != null) {
                            return doc.getText(0, doc.getLength());
                        } else {
                            // Document not yet loaded, open it first
                            doc = editorCookie.openDocument();
                            if (doc != null) {
                                return doc.getText(0, doc.getLength());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current editor content", e);
        }
        return null;
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
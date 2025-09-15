package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.websocket.api.Session;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
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
import org.netbeans.api.project.ui.OpenProjects;
import java.io.IOException;
import org.openbeans.claude.netbeans.tools.CheckDocumentDirty;
import org.openbeans.claude.netbeans.tools.CloseAllDiffTabs;
import org.openbeans.claude.netbeans.tools.CloseTab;
import org.openbeans.claude.netbeans.tools.GetCurrentSelection;
import org.openbeans.claude.netbeans.tools.GetDiagnostics;
import org.openbeans.claude.netbeans.tools.GetOpenEditors;
import org.openbeans.claude.netbeans.tools.GetWorkspaceFolders;
import org.openbeans.claude.netbeans.tools.OpenDiff;
import org.openbeans.claude.netbeans.tools.OpenFile;
import org.openbeans.claude.netbeans.tools.SaveDocument;

/**
 * Handles Model Context Protocol messages and provides NetBeans IDE capabilities
 * to Claude Code through MCP primitives (Tools, Resources, Prompts).
 */
public class NetBeansMCPHandler {

    
    private static final Logger LOGGER = Logger.getLogger(NetBeansMCPHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MCPResponseBuilder responseBuilder;
    private Session webSocketSession;
    
    // Selection tracking
    private final Map<JTextComponent, CaretListener> selectionListeners = new WeakHashMap<>();
    private PropertyChangeListener topComponentListener;
    private JTextComponent currentTextComponent;
    
    private final CheckDocumentDirty checkDocumentDirtyTool;
    private final CloseAllDiffTabs closeAllDiffTabsTool;
    private final CloseTab closeTabTool;
    private final GetCurrentSelection getCurrentSelectionTool;
    private final GetDiagnostics getDiagnosticsTool;
    private final GetOpenEditors getOpenEditorsTool;
    private final GetWorkspaceFolders getWorkspaceFoldersTool;
    private final OpenDiff openDiffTool;
    private final OpenFile openFileTool;
    private final SaveDocument saveDocument;

    public NetBeansMCPHandler() {
        this.responseBuilder = new MCPResponseBuilder(objectMapper);
        this.checkDocumentDirtyTool = new CheckDocumentDirty();
        this.closeAllDiffTabsTool = new CloseAllDiffTabs();
        this.closeTabTool = new CloseTab();
        this.getCurrentSelectionTool = new GetCurrentSelection();
        this.getDiagnosticsTool = new GetDiagnostics();
        this.getOpenEditorsTool = new GetOpenEditors();
        this.getWorkspaceFoldersTool = new GetWorkspaceFolders();
        this.openDiffTool = new OpenDiff();
        this.openFileTool = new OpenFile();
        this.saveDocument = new SaveDocument();
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
                    return responseBuilder.createToolResponse(this.openFileTool.run(this.openFileTool.parseArguments(arguments)));

                case "getWorkspaceFolders":
                    return responseBuilder.createToolResponse(this.getWorkspaceFoldersTool.run(this.getWorkspaceFoldersTool.parseArguments(arguments)));

                case "getOpenEditors":
                    return responseBuilder.createToolResponse(this.getOpenEditorsTool.run(this.getOpenEditorsTool.parseArguments(arguments)));

                case "getCurrentSelection":
                    return responseBuilder.createToolResponse(this.getCurrentSelectionTool.run(this.getCurrentSelectionTool.parseArguments(arguments)));

                case "close_tab":
                    return responseBuilder.createToolResponse(this.closeTabTool.run(this.closeTabTool.parseArguments(arguments)));

                case "getDiagnostics":
                    return responseBuilder.createToolResponse(this.getDiagnosticsTool.run(this.getDiagnosticsTool.parseArguments(arguments)));

                case "checkDocumentDirty":
                    return responseBuilder.createToolResponse(this.checkDocumentDirtyTool.run(this.checkDocumentDirtyTool.parseArguments(arguments)));

                case "saveDocument":
                    return responseBuilder.createToolResponse(this.saveDocument.run(this.saveDocument.parseArguments(arguments)));

                case "closeAllDiffTabs":
                    return responseBuilder.createToolResponse(this.closeAllDiffTabsTool.run(this.closeAllDiffTabsTool.parseArguments(arguments)));

                case "openDiff":
                    return responseBuilder.createToolResponse(this.openDiffTool.run(this.openDiffTool.parseArguments(arguments)));

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
            //XXX: This is probably doing the wrong thing
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
        if (!NbUtils.isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File read denied: Path is not within any open project directory: " + filePath);
        }
        
        Path path = Paths.get(filePath);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        
        return responseBuilder.createToolResponse(content);
    }
    
    // Helper methods
    
    private JsonNode getProjectInfo(String projectPath) {
        FileObject projectDir = FileUtil.toFileObject(new File(projectPath));
        if (projectDir == null) {
            throw new IllegalArgumentException("Project not found: " + projectPath);
        }
        
        ObjectNode projectInfo = responseBuilder.objectNode();
        projectInfo.put("path", projectPath);
        projectInfo.put("name", projectDir.getName());
        
        ArrayNode files = responseBuilder.arrayNode();
        // projectInfo.set("files", files);
        
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
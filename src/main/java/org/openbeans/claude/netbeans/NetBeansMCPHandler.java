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
import java.util.LinkedHashMap;
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
import org.openbeans.claude.netbeans.tools.AsyncResponse;
import org.openbeans.claude.netbeans.tools.CheckDocumentDirty;
import org.openbeans.claude.netbeans.tools.CloseAllDiffTabs;
import org.openbeans.claude.netbeans.tools.CloseTab;
import org.openbeans.claude.netbeans.tools.DiffTabTracker;
import org.openbeans.claude.netbeans.tools.GetCurrentSelection;
import org.openbeans.claude.netbeans.tools.GetDiagnostics;
import org.openbeans.claude.netbeans.tools.GetOpenEditors;
import org.openbeans.claude.netbeans.tools.GetWorkspaceFolders;
import org.openbeans.claude.netbeans.tools.OpenDiff;
import org.openbeans.claude.netbeans.tools.OpenFile;
import org.openbeans.claude.netbeans.tools.SaveDocument;
import org.openbeans.claude.netbeans.tools.Tool;

/**
 * Handles Model Context Protocol messages and provides NetBeans IDE capabilities
 * to Claude Code through MCP primitives (Tools, Resources, Prompts).
 */
public class NetBeansMCPHandler {

    private static final Logger LOGGER = Logger.getLogger(NetBeansMCPHandler.class.getName());
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MCPResponseBuilder responseBuilder;
    private volatile Session webSocketSession;
    
    // Selection tracking
    private final Map<JTextComponent, CaretListener> selectionListeners = new WeakHashMap<>();
    private PropertyChangeListener topComponentListener;
    private PropertyChangeListener diffTabListener;
    private JTextComponent currentTextComponent;

    /** Tools by MCP name. */
    private final Map<String, Tool<?, ?>> tools = new LinkedHashMap<>();

    public NetBeansMCPHandler() {
        this(List.of(
                new OpenFile(),
                new GetWorkspaceFolders(),
                new GetOpenEditors(),
                new GetCurrentSelection(),
                new CloseTab(),
                new GetDiagnostics(),
                new CheckDocumentDirty(),
                new SaveDocument(),
                new CloseAllDiffTabs(),
                new OpenDiff()));
    }

    /** Creates a handler serving the given tools (used by tests). */
    NetBeansMCPHandler(List<Tool<?, ?>> toolList) {
        this.responseBuilder = new MCPResponseBuilder(objectMapper);
        for (Tool<?, ?> tool : toolList) {
            tools.put(tool.getName(), tool);
        }
    }

    /** A JSON-RPC error to be returned to the client. */
    private static final class ProtocolException extends Exception {
        final int code;

        ProtocolException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
    
    /**
     * Handles an incoming JSON-RPC message and routes it to the appropriate handler.
     * Notifications (messages without an id) never get a response.
     * 
     * @param message the JSON-RPC message
     * @return response JSON string, or null if no response must be sent
     */
    public String handleMessage(JsonNode message) {
        JsonNode id = message.get("id");
        boolean isNotification = id == null;
        String method = message.path("method").isTextual() ? message.get("method").asText() : null;

        if (method == null) {
            if (!isNotification && (message.has("result") || message.has("error"))) {
                // A response to a server-initiated request; we send none, so ignore it.
                LOGGER.log(Level.FINE, "Ignoring JSON-RPC response with id {0}", id);
                return null;
            }
            return isNotification ? null
                    : responseBuilder.createErrorResponse(id, MCPResponseBuilder.INVALID_REQUEST, "Invalid Request", "Missing method");
        }

        if (isNotification) {
            // e.g. notifications/initialized, notifications/cancelled
            LOGGER.log(Level.FINE, "Received notification: {0}", method);
            return null;
        }

        LOGGER.log(Level.FINE, "Processing MCP method: {0}", method);
        try {
            JsonNode params = message.get("params");
            JsonNode result;
            switch (method) {
                case "initialize":
                    result = handleInitialize(params);
                    break;
                case "ping":
                    result = responseBuilder.objectNode();
                    break;
                case "tools/list":
                    result = handleToolsList();
                    break;
                case "tools/call":
                    result = handleToolsCall(params, id);
                    if (result == null) {
                        // Async tool - the response is sent later
                        return null;
                    }
                    break;
                case "resources/list":
                    result = handleResourcesList();
                    break;
                case "resources/read":
                    result = handleResourcesRead(params);
                    break;
                default:
                    LOGGER.log(Level.WARNING, "Unknown MCP method: {0}", method);
                    return responseBuilder.createErrorResponse(id, MCPResponseBuilder.METHOD_NOT_FOUND, "Method not found", method);
            }
            return objectMapper.writeValueAsString(responseBuilder.createResponse(id, result));

        } catch (ProtocolException e) {
            LOGGER.log(Level.WARNING, "Rejected MCP request {0}: {1}", new Object[]{method, e.getMessage()});
            return responseBuilder.createErrorResponse(id, e.code, e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling MCP message", e);
            return responseBuilder.createErrorResponse(id, MCPResponseBuilder.INTERNAL_ERROR, "Internal error", e.getMessage());
        }
    }
    
    /**
     * Handles MCP initialize request. Only capabilities that are actually
     * implemented are advertised.
     */
    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode result = responseBuilder.objectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "netbeans-mcp-server");
        serverInfo.put("version", "1.0.0");
        
        return result;
    }
    
    /**
     * Lists available tools (executable functions).
     */
    private JsonNode handleToolsList() {
        ArrayNode toolList = responseBuilder.arrayNode();
        
        // Core Claude Code tools - names/descriptions in code, schemas from JSON
        toolList.add(createToolDefinition("openFile", "Opens a file in the editor", "OpenFileParams"));
        toolList.add(createToolDefinition("getWorkspaceFolders", "Get list of workspace folders (open projects)", "getWorkspaceFolders"));
        toolList.add(createToolDefinition("getOpenEditors", "Get list of currently open editor tabs", "getOpenEditors"));
        toolList.add(createToolDefinition("getCurrentSelection", "Get the current text selection in the active editor", "getCurrentSelection"));
        toolList.add(createToolDefinition("close_tab", "Close an open editor tab", "CloseTabParams"));
        toolList.add(createToolDefinition("getDiagnostics", "Get diagnostic information (errors, warnings) for files", "GetDiagnosticsParams"));
        toolList.add(createToolDefinition("checkDocumentDirty", "Check if a document has unsaved changes", "CheckDocumentDirtyParams"));
        toolList.add(createToolDefinition("saveDocument", "Save a document to disk", "SaveDocumentParams"));
        toolList.add(createToolDefinition("closeAllDiffTabs", "Close all diff viewer tabs", "CloseAllDiffTabsParams"));
        toolList.add(createToolDefinition("openDiff", "Open a git diff for the file", "OpenDiffParams"));
        
        ObjectNode result = responseBuilder.objectNode();
        result.set("tools", toolList);
        return result;
    }
    
    /**
     * Handles tool call requests.
     *
     * Unknown tools and unparseable arguments are protocol errors; failures
     * while running a tool are reported as a result with isError set, so the
     * model can see and react to them.
     *
     * @param params Tool call parameters
     * @param requestId Request ID for async response handling
     * @return the CallToolResult for sync tools, null for async tools
     */
    private JsonNode handleToolsCall(JsonNode params, JsonNode requestId) throws ProtocolException {
        if (params == null || !params.path("name").isTextual()) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS, "Missing tool name");
        }
        String toolName = params.get("name").asText();
        Tool<?, ?> tool = tools.get(toolName);
        if (tool == null) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS, "Unknown tool: " + toolName);
        }

        Object result;
        try {
            result = invoke(tool, params.get("arguments"));
        } catch (InvalidArgumentsException e) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS,
                    "Invalid arguments for tool " + toolName + ": " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing tool: " + toolName, e);
            return responseBuilder.createToolErrorResponse("Error: " + e.getMessage());
        }

        if (result instanceof AsyncResponse) {
            @SuppressWarnings("unchecked")
            AsyncResponse<Object> async = (AsyncResponse<Object>) result;
            async.setHandler(finalResult -> sendAsyncToolResponse(requestId, finalResult));
            return null;
        }
        return responseBuilder.createToolResponse(result);
    }

    private static final class InvalidArgumentsException extends Exception {
        InvalidArgumentsException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static <T, O> Object invoke(Tool<T, O> tool, JsonNode arguments) throws Exception {
        T parsed;
        try {
            parsed = tool.parseArguments(arguments);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentsException(e);
        }
        return tool.run(parsed);
    }

    /**
     * Sends an async tool response via WebSocket.
     * @param requestId The original request ID
     * @param result The tool result to send
     */
    private void sendAsyncToolResponse(JsonNode requestId, Object result) {
        try {
            String message = objectMapper.writeValueAsString(
                    responseBuilder.createResponse(requestId, responseBuilder.createToolResponse(result)));
            send(message);
            LOGGER.log(Level.INFO, "Sent async tool response for request ID: {0}", requestId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending async tool response", e);
        }
    }

    /**
     * Sends a message to the connected client, if any.
     *
     * @param message serialized JSON-RPC message
     */
    void send(String message) {
        Session session = webSocketSession;
        if (session == null || !session.isOpen()) {
            LOGGER.warning("Cannot send message - WebSocket not open");
            return;
        }
        try {
            session.getRemote().sendString(message);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to send WebSocket message", e);
        }
    }
    
    /**
     * Lists available resources: one per open project.
     */
    private JsonNode handleResourcesList() {
        ArrayNode resources = responseBuilder.arrayNode();
        for (Project project : OpenProjects.getDefault().getOpenProjects()) {
            String path = project.getProjectDirectory().getPath();
            String displayName = ProjectUtils.getInformation(project).getDisplayName();
            ObjectNode resource = resources.addObject();
            resource.put("uri", "project://" + path);
            resource.put("name", displayName);
            resource.put("description", "NetBeans project: " + displayName);
            resource.put("mimeType", "application/json");
        }
        ObjectNode result = responseBuilder.objectNode();
        result.set("resources", resources);
        return result;
    }
    
    /**
     * Reads a resource. Returns { contents: [ { uri, mimeType, text } ] }.
     */
    private JsonNode handleResourcesRead(JsonNode params) throws Exception {
        if (params == null || !params.path("uri").isTextual()) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS, "Missing resource uri");
        }
        String uri = params.get("uri").asText();
        if (!uri.startsWith("project://")) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS, "Unknown resource URI: " + uri);
        }
        String projectPath = uri.substring("project://".length());
        // Only expose projects that are actually open
        Project project = null;
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            if (p.getProjectDirectory().getPath().equals(projectPath)) {
                project = p;
                break;
            }
        }
        if (project == null) {
            throw new ProtocolException(MCPResponseBuilder.INVALID_PARAMS, "Project not open: " + projectPath);
        }

        ObjectNode projectInfo = responseBuilder.objectNode();
        projectInfo.put("path", projectPath);
        projectInfo.put("name", ProjectUtils.getInformation(project).getDisplayName());

        ObjectNode result = responseBuilder.objectNode();
        ObjectNode item = result.putArray("contents").addObject();
        item.put("uri", uri);
        item.put("mimeType", "application/json");
        item.put("text", objectMapper.writeValueAsString(projectInfo));
        return result;
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
            // Start tracking selection changes and diff tabs when connected
            startSelectionTracking();
            startDiffTabTracking();
        } else {
            // Stop tracking when disconnected
            stopSelectionTracking();
            stopDiffTabTracking();
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
     * Starts tracking diff tab closures for async response handling.
     */
    private void startDiffTabTracking() {
        diffTabListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (TopComponent.Registry.PROP_TC_CLOSED.equals(evt.getPropertyName())) {
                    Object closed = evt.getNewValue();
                    if (closed != null) {
                        // No-op if the diff was already accepted or superseded
                        DiffTabTracker.reject(closed);
                    }
                }
            }
        };

        TopComponent.getRegistry().addPropertyChangeListener(diffTabListener);
        LOGGER.log(Level.FINE, "Started diff tab tracking");
    }

    /**
     * Stops tracking diff tab closures.
     */
    private void stopDiffTabTracking() {
        if (diffTabListener != null) {
            TopComponent.getRegistry().removePropertyChangeListener(diffTabListener);
            diffTabListener = null;
        }
        LOGGER.log(Level.FINE, "Stopped diff tab tracking");
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
                    send(message);
                    
                    LOGGER.log(Level.FINE, "Sent selection_changed event: {0}", message);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error sending selection change event", e);
        }
    }
}
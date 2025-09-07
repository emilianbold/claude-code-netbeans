package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.editor.AnnotationDesc;
import org.netbeans.editor.Annotations;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.GetDiagnosticsParams;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Tool to get diagnostic information (errors, warnings) for files.
 */
public class GetDiagnostics implements Tool<GetDiagnosticsParams> {
    
    private static final Logger LOGGER = Logger.getLogger(GetDiagnostics.class.getName());
    
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
    
    @Override
    public String getName() {
        return "getDiagnostics";
    }
    
    @Override
    public String getDescription() {
        return "Get diagnostic information (errors, warnings) for files";
    }
    
    @Override
    public Class<GetDiagnosticsParams> getParameterClass() {
        return GetDiagnosticsParams.class;
    }
    
    @Override
    public JsonNode run(GetDiagnosticsParams params, MCPResponseBuilder responseBuilder) throws Exception {
        String uri = params.getUri();
        
        try {
            if (uri != null) {
                // Get diagnostics for a specific file
                return getDiagnosticsForFile(uri, responseBuilder);
            } else {
                // Get diagnostics for all open files
                return getDiagnosticsForAllFiles(responseBuilder);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics", e);
            return responseBuilder.createToolResponse("Failed to get diagnostics: " + e.getMessage());
        }
    }
    
    /**
     * Extracts diagnostic information from NetBeans annotations for a specific file.
     */
    private JsonNode getDiagnosticsForFile(String uri, MCPResponseBuilder responseBuilder) {
        try {
            // Convert URI to file path
            String filePath = uri.startsWith("file://") ? uri.substring(7) : uri;
            
            // Security check: Only allow files within open projects
            if (!NbUtils.isPathWithinOpenProjects(filePath)) {
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
    private JsonNode getDiagnosticsForAllFiles(MCPResponseBuilder responseBuilder) {
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
}
package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.editor.AnnotationDesc;
import org.netbeans.editor.Annotations;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.Diagnostic;
import org.openbeans.claude.netbeans.tools.params.DiagnosticsResponse;
import org.openbeans.claude.netbeans.tools.params.GetDiagnosticsParams;
import org.openbeans.claude.netbeans.tools.params.Range;
import org.openbeans.claude.netbeans.tools.params.Start;
import org.openbeans.claude.netbeans.tools.params.End;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Tool to get diagnostic information (errors, warnings) for files.
 */
public class GetDiagnostics implements Tool<GetDiagnosticsParams, String> {
    
    private static final Logger LOGGER = Logger.getLogger(GetDiagnostics.class.getName());
    
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

    private List<DiagnosticsResponse> _run(GetDiagnosticsParams params) throws Exception {
        String uri = params.getUri();

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
            return new ArrayList<>();
        }
    }

    @Override
    public String run(GetDiagnosticsParams params) throws Exception {
        try {
            List<DiagnosticsResponse> diagnostics = _run(params);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(diagnostics);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to serialize diagnostics", e);
            return "[]";
        }
    }

    /**
     * Creates a Diagnostic object from annotation data.
     */
    private Diagnostic createDiagnostic(String message, String severity, String source,
                                      String code, int line, int column) {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setMessage(message);

        // Convert severity to enum
        Diagnostic.Severity severityEnum;
        if ("error".equalsIgnoreCase(severity)) {
            severityEnum = Diagnostic.Severity.ERROR;
        } else if ("warning".equalsIgnoreCase(severity)) {
            severityEnum = Diagnostic.Severity.WARNING;
        } else {
            severityEnum = Diagnostic.Severity.INFO;
        }
        diagnostic.setSeverity(severityEnum);

        diagnostic.setSource(source);
        diagnostic.setCode(code);

        // Create range (convert to 0-based line numbers)
        Start start = new Start();
        start.setLine(line - 1);
        start.setCharacter(column);

        End end = new End();
        end.setLine(line - 1);
        end.setCharacter(column);

        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);

        diagnostic.setRange(range);

        return diagnostic;
    }
    
    /**
     * Extracts diagnostic information from NetBeans annotations for a specific file.
     */
    private List<DiagnosticsResponse> getDiagnosticsForFile(String uri) {
        try {
            // Convert URI to file path
            String filePath = uri.startsWith("file://") ? uri.substring(7) : uri;
            
            // Security check: Only allow files within open projects
            if (!NbUtils.isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File access denied: Path is not within any open project directory: " + filePath);
            }
            
            List<Diagnostic> diagnostics = extractDiagnosticsFromFile(filePath);
            if (!diagnostics.isEmpty()) {
                DiagnosticsResponse response = new DiagnosticsResponse();
                response.setUri(URI.create("file://" + filePath));
                response.setDiagnostics(diagnostics);
                return List.of(response);
            }
            return new ArrayList<>();
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics for file: " + uri, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets diagnostics for all currently open files.
     */
    private List<DiagnosticsResponse> getDiagnosticsForAllFiles() {
        try {
            List<DiagnosticsResponse> allResponses = new ArrayList<>();
            
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
                                List<Diagnostic> fileDiagnostics = extractDiagnosticsFromFile(file.getAbsolutePath());
                                if (!fileDiagnostics.isEmpty()) {
                                    DiagnosticsResponse response = new DiagnosticsResponse();
                                    response.setUri(URI.create("file://" + file.getAbsolutePath()));
                                    response.setDiagnostics(fileDiagnostics);
                                    allResponses.add(response);
                                }
                            }
                        }
                    }
                }
            }
            
            return allResponses;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics for all files", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Extracts diagnostic information from a specific file using NetBeans editor annotations.
     */
    private List<Diagnostic> extractDiagnosticsFromFile(String filePath) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        
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
                                        Diagnostic diagnostic = convertAnnotationToDiagnostic(
                                            activeAnnotation, currentLine + 1, 0);
                                        if (diagnostic != null) {
                                            diagnostics.add(diagnostic);
                                        }
                                    }
                                    
                                    // Get all passive annotations for this line
                                    AnnotationDesc[] passiveAnnotations = annotations.getPassiveAnnotationsForLine(currentLine);
                                    if (passiveAnnotations != null) {
                                        for (AnnotationDesc annotation : passiveAnnotations) {
                                            Diagnostic diagnostic = convertAnnotationToDiagnostic(
                                                annotation, currentLine + 1, 0);
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
     * Converts a NetBeans AnnotationDesc to a Diagnostic object.
     */
    private Diagnostic convertAnnotationToDiagnostic(AnnotationDesc annotation,
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
        
        return createDiagnostic(message, severity, source, code, lineNumber, columnNumber);
    }
}
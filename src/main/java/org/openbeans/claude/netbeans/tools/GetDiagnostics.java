package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import org.netbeans.editor.AnnotationDesc;
import org.netbeans.editor.Annotations;
import org.netbeans.editor.BaseDocument;
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
import org.openide.windows.WindowManager;

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
                                      String code, Range range) {
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

        diagnostic.setRange(range);

        return diagnostic;
    }
    
    /**
     * Computes the zero-based range of an annotation. Annotations covering a
     * whole line (negative length) span to the end of that line; if the
     * offset is unusable, the start of the given line is used.
     *
     * @param doc the document
     * @param offset annotation start offset
     * @param length annotation length, negative for the whole line
     * @param fallbackLine zero-based line to use when the offset is invalid
     * @return the range
     */
    static Range rangeOf(Document doc, int offset, int length, int fallbackLine) {
        if (!(doc instanceof StyledDocument) || offset < 0 || offset > doc.getLength()) {
            return range(fallbackLine, 0, fallbackLine, 0);
        }
        StyledDocument styled = (StyledDocument) doc;
        int endOffset;
        if (length < 0) {
            Element root = doc.getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(offset));
            endOffset = Math.max(offset, line.getEndOffset() - 1);
        } else {
            endOffset = offset + length;
        }
        endOffset = Math.min(endOffset, doc.getLength());
        NbUtils.Position start = NbUtils.position(styled, offset);
        NbUtils.Position end = NbUtils.position(styled, endOffset);
        return range(start.line, start.character, end.line, end.character);
    }

    private static Range range(int startLine, int startChar, int endLine, int endChar) {
        Start start = new Start();
        start.setLine(startLine);
        start.setCharacter(startChar);
        End end = new End();
        end.setLine(endLine);
        end.setCharacter(endChar);
        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);
        return range;
    }
    
    /**
     * Extracts diagnostic information from NetBeans annotations for a specific file.
     */
    private List<DiagnosticsResponse> getDiagnosticsForFile(String uri) {
        try {
            // Convert URI to file path
            String filePath = NbUtils.toFilePath(uri);
            
            // Security check: Only allow files within open projects
            if (!NbUtils.isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File access denied: Path is not within any open project directory: " + filePath);
            }
            
            List<Diagnostic> diagnostics = extractDiagnosticsFromFile(filePath);
            if (!diagnostics.isEmpty()) {
                DiagnosticsResponse response = new DiagnosticsResponse();
                response.setUri(URI.create(NbUtils.toFileUri(filePath)));
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
     * Gets diagnostics for all files open in editor windows. Other windows
     * (Projects, Navigator, ...) are skipped and each file is reported once,
     * even if it is open in several editors.
     */
    private List<DiagnosticsResponse> getDiagnosticsForAllFiles() {
        try {
            List<DiagnosticsResponse> allResponses = new ArrayList<>();
            for (FileObject fileObject : openEditorFiles()) {
                File file = FileUtil.toFile(fileObject);
                if (file == null) {
                    continue;
                }
                List<Diagnostic> fileDiagnostics = extractDiagnosticsFromFile(file.getAbsolutePath());
                if (!fileDiagnostics.isEmpty()) {
                    DiagnosticsResponse response = new DiagnosticsResponse();
                    response.setUri(URI.create(NbUtils.toFileUri(file.getAbsolutePath())));
                    response.setDiagnostics(fileDiagnostics);
                    allResponses.add(response);
                }
            }
            return allResponses;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get diagnostics for all files", e);
            return new ArrayList<>();
        }
    }

    /** Files shown in open editor windows, each listed once. */
    private static Set<FileObject> openEditorFiles() {
        Set<FileObject> files = new LinkedHashSet<>();
        WindowManager wm = WindowManager.getDefault();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!wm.isOpenedEditorTopComponent(tc)) {
                continue;
            }
            Node[] nodes = tc.getActivatedNodes();
            if (nodes == null || nodes.length == 0) {
                continue;
            }
            DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
            if (dataObject != null && dataObject.getPrimaryFile() != null) {
                files.add(dataObject.getPrimaryFile());
            }
        }
        return files;
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
                        if (doc instanceof BaseDocument) {
                            for (LineAnnotation a : annotationsOf(((BaseDocument) doc).getAnnotations())) {
                                Diagnostic diagnostic = convertAnnotationToDiagnostic(a.annotation, doc, a.line);
                                if (diagnostic != null) {
                                    diagnostics.add(diagnostic);
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
    
    /** An annotation together with the zero-based line it is attached to. */
    static final class LineAnnotation {
        final AnnotationDesc annotation;
        final int line;

        LineAnnotation(AnnotationDesc annotation, int line) {
            this.annotation = annotation;
            this.line = line;
        }
    }

    /**
     * Collects every annotation (active and passive) of a document.
     * {@link Annotations#getNextLineWithAnnotation(int)} returns the first
     * annotated line at or after its argument, so the search continues from
     * the following line.
     */
    static List<LineAnnotation> annotationsOf(Annotations annotations) {
        List<LineAnnotation> result = new ArrayList<>();
        if (annotations == null) {
            return result;
        }
        int line = annotations.getNextLineWithAnnotation(0);
        while (line >= 0) {
            AnnotationDesc active = annotations.getActiveAnnotation(line);
            if (active != null) {
                result.add(new LineAnnotation(active, line));
            }
            AnnotationDesc[] passive = annotations.getPassiveAnnotationsForLine(line);
            if (passive != null) {
                for (AnnotationDesc annotation : passive) {
                    result.add(new LineAnnotation(annotation, line));
                }
            }
            int next = annotations.getNextLineWithAnnotation(line + 1);
            if (next <= line) {
                break;
            }
            line = next;
        }
        return result;
    }

    /**
     * Maps a NetBeans annotation type to a severity. Java errors and warnings
     * use types such as {@code org-netbeans-spi-editor-hints-parser_annotation_err}
     * and {@code ..._warn}.
     *
     * @return "error", "warning", "hint" or "info"
     */
    static String severityOf(String annotationType) {
        if (annotationType == null) {
            return "info";
        }
        String type = annotationType.toLowerCase();
        if (type.contains("_err") || type.contains("error")) {
            return "error";
        }
        if (type.contains("warn")) {
            return "warning";
        }
        if (type.contains("hint") || type.contains("verifier")) {
            return "hint";
        }
        return "info";
    }

    /**
     * Removes the UI help NetBeans appends to hint descriptions, such as
     * {@code "\n----\n(Alt-Enter shows hints)"}.
     *
     * @return the trimmed message, empty if there is none
     */
    static String cleanMessage(String description) {
        if (description == null) {
            return "";
        }
        return description.replaceFirst("(?s)\\R-{3,}\\R.*$", "").trim();
    }

    /**
     * Converts a NetBeans AnnotationDesc to a Diagnostic object.
     */
    Diagnostic convertAnnotationToDiagnostic(AnnotationDesc annotation,
                                                   Document doc, int line) {
        if (annotation == null) {
            return null;
        }
        
        String severity = severityOf(annotation.getAnnotationType());
        if ("info".equals(severity)) {
            // Not a problem marker: override/implements glyphs, bookmarks, breakpoints, ...
            return null;
        }

        String message = cleanMessage(annotation.getShortDescription());
        if (message.isEmpty()) {
            return null;
        }

        String source = "hint".equals(severity) ? "editor" : "compiler";
        String code = null;

        return createDiagnostic(message, severity, source, code,
                rangeOf(doc, annotation.getOffset(), annotation.getLength(), line));
    }
}
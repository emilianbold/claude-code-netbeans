package org.openbeans.claude.netbeans.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.netbeans.editor.AnnotationDesc;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openbeans.claude.netbeans.tools.params.Diagnostic;
import org.openbeans.claude.netbeans.tools.params.GetDiagnosticsParams;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the package-private helper methods in GetDiagnostics.
 * These methods contain pure business logic with no NetBeans platform dependencies.
 */
public class GetDiagnosticsInternalTest {

    private final GetDiagnostics tool = new GetDiagnostics();

    // ---- createDiagnostic ----

    @Test
    public void createDiagnostic_severity_error_setsEnumAndCompilerSource() {
        Diagnostic d = tool.createDiagnostic("compilation failed", "error", "compiler", null, 5, 0);
        assertNotNull(d);
        assertEquals("compilation failed", d.getMessage());
        assertEquals(Diagnostic.Severity.ERROR, d.getSeverity());
        assertEquals("compiler", d.getSource());
        assertNull(d.getCode());
        assertEquals(4, d.getRange().getStart().getLine()); // 0-based: line 5 -> 4
    }

    @Test
    public void createDiagnostic_severity_warning_setsEnumAndSource() {
        Diagnostic d = tool.createDiagnostic("unchecked cast", "warning", "compiler", "unchecked", 10, 3);
        assertNotNull(d);
        assertEquals(Diagnostic.Severity.WARNING, d.getSeverity());
        assertEquals("unchecked", d.getCode());
        assertEquals(9, d.getRange().getStart().getLine());
        assertEquals(3, d.getRange().getStart().getCharacter());
    }

    @Test
    public void createDiagnostic_severity_info_defaultsToInfo() {
        Diagnostic d = tool.createDiagnostic("some info", "info", "editor", null, 1, 0);
        assertEquals(Diagnostic.Severity.INFO, d.getSeverity());
    }

    @Test
    public void createDiagnostic_unknownSeverity_defaultsToInfo() {
        Diagnostic d = tool.createDiagnostic("hint text", "hint", "editor", null, 2, 0);
        assertEquals(Diagnostic.Severity.INFO, d.getSeverity());
    }

    @Test
    public void createDiagnostic_withExplicitCode_setsCode() {
        // code is passed by the caller; createDiagnostic just stores it
        Diagnostic d = tool.createDiagnostic("some error [E001]", "error", "compiler", "E001", 1, 0);
        assertEquals("E001", d.getCode());
    }

    @Test
    public void createDiagnostic_nullCode_codeIsNull() {
        Diagnostic d = tool.createDiagnostic("plain error message", "error", "compiler", null, 1, 0);
        assertNull(d.getCode());
    }

    @Test
    public void createDiagnostic_rangeStartAndEndMatch() {
        Diagnostic d = tool.createDiagnostic("msg", "error", "src", null, 7, 12);
        assertEquals(6, d.getRange().getStart().getLine());
        assertEquals(12, d.getRange().getStart().getCharacter());
        assertEquals(6, d.getRange().getEnd().getLine());
        assertEquals(12, d.getRange().getEnd().getCharacter());
    }

    // ---- convertAnnotationToDiagnostic ----

    @Test
    public void convertAnnotationToDiagnostic_null_returnsNull() {
        assertNull(tool.convertAnnotationToDiagnostic(null, 1, 0));
    }

    @Test
    public void convertAnnotationToDiagnostic_nullMessage_returnsNull() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn(null);
        assertNull(tool.convertAnnotationToDiagnostic(ann, 1, 0));
    }

    @Test
    public void convertAnnotationToDiagnostic_blankMessage_returnsNull() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("   ");
        assertNull(tool.convertAnnotationToDiagnostic(ann, 1, 0));
    }

    @Test
    public void convertAnnotationToDiagnostic_errorAnnotationType_setsErrorSeverity() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Cannot find symbol");
        Mockito.when(ann.getAnnotationType()).thenReturn("org-netbeans-modules-java-errors");
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 5, 0);
        assertNotNull(d);
        assertEquals(Diagnostic.Severity.ERROR, d.getSeverity());
        assertEquals("compiler", d.getSource());
    }

    @Test
    public void convertAnnotationToDiagnostic_warningAnnotationType_setsWarningSeverity() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Unchecked cast");
        Mockito.when(ann.getAnnotationType()).thenReturn("org-netbeans-modules-java-warning");
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 3, 0);
        assertNotNull(d);
        assertEquals(Diagnostic.Severity.WARNING, d.getSeverity());
        assertEquals("compiler", d.getSource());
    }

    @Test
    public void convertAnnotationToDiagnostic_hintAnnotationType_setsInfoWithEditorSource() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Consider using var");
        Mockito.when(ann.getAnnotationType()).thenReturn("org-netbeans-modules-hint-suggestion");
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 2, 0);
        assertNotNull(d);
        assertEquals("editor", d.getSource());
    }

    @Test
    public void convertAnnotationToDiagnostic_unknownType_defaultsToInfo() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Some annotation");
        Mockito.when(ann.getAnnotationType()).thenReturn("org-some-unknown-type");
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 1, 0);
        assertNotNull(d);
        assertEquals(Diagnostic.Severity.INFO, d.getSeverity());
        assertEquals("netbeans", d.getSource());
    }

    @Test
    public void convertAnnotationToDiagnostic_nullAnnotationType_defaultsToInfo() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Some message");
        Mockito.when(ann.getAnnotationType()).thenReturn(null);
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 1, 0);
        assertNotNull(d);
        assertEquals(Diagnostic.Severity.INFO, d.getSeverity());
    }

    @Test
    public void convertAnnotationToDiagnostic_messageWithCode_extractsCode() {
        AnnotationDesc ann = Mockito.mock(AnnotationDesc.class);
        Mockito.when(ann.getShortDescription()).thenReturn("Error description [ERR_42]");
        Mockito.when(ann.getAnnotationType()).thenReturn("errors");
        Diagnostic d = tool.convertAnnotationToDiagnostic(ann, 1, 0);
        assertNotNull(d);
        assertEquals("ERR_42", d.getCode());
    }

    // ---- extractDiagnosticsFromFile ----

    @Test
    public void extractDiagnosticsFromFile_nonExistentPath_returnsEmptyList() {
        // FileUtil.toFileObject returns null for a path that doesn't exist → empty list
        List<Diagnostic> result = tool.extractDiagnosticsFromFile("/nonexistent_for_testing_xyz/file.java");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void extractDiagnosticsFromFile_existingTempFile_returnsListWithoutThrowing() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("nbdiag", ".java");
        tmp.deleteOnExit();

        // With a real file: FileUtil may return a FileObject; DataObject.find may throw
        // DataObjectNotFoundException which is caught → empty list returned
        List<Diagnostic> result = tool.extractDiagnosticsFromFile(tmp.getAbsolutePath());
        assertNotNull(result);
    }

    // ---- getDiagnosticsForFile via isPathAllowed bypass ----

    @Test
    public void run_pathAllowed_nonExistentFile_returnsEmptyJsonArray() throws Exception {
        GetDiagnostics pathBypassedTool = new GetDiagnostics() {
            @Override
            protected boolean isPathAllowed(String filePath) { return true; }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        params.setUri("/nonexistent_for_testing_xyz/file.java");

        String result = pathBypassedTool.run(params);

        assertEquals("[]", result);
    }

    @Test
    public void run_pathAllowed_withFakeDiagnostic_returnsNonEmptyJson() throws Exception {
        // Overriding extractDiagnosticsFromFile to return a fake diagnostic covers the
        // getDiagnosticsForFile success path (response created and returned)
        GetDiagnostics injectedTool = new GetDiagnostics() {
            @Override
            protected boolean isPathAllowed(String filePath) { return true; }

            @Override
            List<Diagnostic> extractDiagnosticsFromFile(String filePath) {
                Diagnostic d = new Diagnostic();
                d.setMessage("fake compiler error");
                d.setSeverity(Diagnostic.Severity.ERROR);
                return List.of(d);
            }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        params.setUri("file:///test/Fake.java");

        String result = injectedTool.run(params);

        assertNotNull(result);
        assertNotEquals("[]", result);
        assertTrue(result.contains("fake compiler error"));
    }

    // ---- getDiagnosticsForAllFiles via getOpenTopComponents override ----

    @Test
    public void getDiagnosticsForAllFiles_tcWithNullNodes_emptyResult() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getActivatedNodes()).thenReturn(null);

        GetDiagnostics t = new GetDiagnostics() {
            @Override
            protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        assertEquals("[]", t.run(params));
    }

    @Test
    public void getDiagnosticsForAllFiles_tcWithNullDataObject_emptyResult() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(null);

        GetDiagnostics t = new GetDiagnostics() {
            @Override
            protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        assertEquals("[]", t.run(params));
    }

    @Test
    public void getDiagnosticsForAllFiles_tcWithNullFileObject_emptyResult() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        DataObject mockDO = Mockito.mock(DataObject.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(null);

        GetDiagnostics t = new GetDiagnostics() {
            @Override
            protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        assertEquals("[]", t.run(params));
    }

    @Test
    public void getDiagnosticsForAllFiles_nonNullFile_emptyDiagnostics_emptyResult() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        File tmpFile = File.createTempFile("nbdiagtest", ".java");
        tmpFile.deleteOnExit();

        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(mockFO);

        GetDiagnostics t = new GetDiagnostics() {
            @Override protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
            @Override protected File fileObjectToFile(FileObject fo) { return tmpFile; }
            @Override List<Diagnostic> extractDiagnosticsFromFile(String filePath) { return new ArrayList<>(); }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        assertEquals("[]", t.run(params));
    }

    @Test
    public void getDiagnosticsForAllFiles_nonNullFile_withDiagnostics_buildsResponse() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        File tmpFile = File.createTempFile("nbdiagtest2", ".java");
        tmpFile.deleteOnExit();

        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(mockFO);

        Diagnostic fakeDiag = new Diagnostic();
        fakeDiag.setMessage("test error");
        fakeDiag.setSeverity(Diagnostic.Severity.ERROR);

        GetDiagnostics t = new GetDiagnostics() {
            @Override protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
            @Override protected File fileObjectToFile(FileObject fo) { return tmpFile; }
            @Override List<Diagnostic> extractDiagnosticsFromFile(String filePath) { return List.of(fakeDiag); }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        String result = t.run(params);

        assertNotNull(result);
        assertNotEquals("[]", result);
        assertTrue(result.contains("test error"));
    }

    @Test
    public void fileObjectToFile_callsFileUtilToFile() {
        // Exercises the protected seam directly; FileUtil.toFile(mockFO) may throw or return null
        GetDiagnostics d = new GetDiagnostics();
        FileObject mockFO = Mockito.mock(FileObject.class);
        try {
            d.fileObjectToFile(mockFO);
        } catch (Throwable ignored) {
            // NB FileUtil may throw in test env — seam body was executed
        }
    }

    @Test
    public void getDiagnosticsForAllFiles_fileObjectToFileReturnsNull_skipsFile() throws Exception {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);

        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(mockFO);

        GetDiagnostics t = new GetDiagnostics() {
            @Override protected java.util.Set<TopComponent> getOpenTopComponents() { return Set.of(mockTC); }
            @Override protected File fileObjectToFile(FileObject fo) { return null; }
        };
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        assertEquals("[]", t.run(params));
    }
}

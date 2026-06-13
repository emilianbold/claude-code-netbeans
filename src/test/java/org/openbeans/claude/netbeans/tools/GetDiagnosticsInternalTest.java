package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.netbeans.editor.AnnotationDesc;
import org.openbeans.claude.netbeans.tools.params.Diagnostic;
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
}

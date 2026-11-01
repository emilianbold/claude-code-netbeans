package org.openbeans.claude.netbeans.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.AnnotationDesc;
import org.netbeans.editor.AnnotationType;
import org.netbeans.editor.AnnotationTypes;
import org.netbeans.editor.BaseDocument;
import static org.junit.jupiter.api.Assertions.*;

public class GetDiagnosticsAnnotationsTest {

    /** Outside the IDE no annotation types are registered, and untyped annotations are dropped. */
    @BeforeAll
    public static void registerAnnotationTypes() {
        Map<String, AnnotationType> types = new HashMap<>();
        for (String name : List.of("first", "third", "fourth")) {
            AnnotationType type = new AnnotationType();
            type.setName(name);
            type.setVisible(true);
            type.setWholeLine(true);
            types.put(name, type);
        }
        AnnotationTypes.getTypes().setTypes(types);
    }

    private static AnnotationDesc annotation(String type, int offset, int line) {
        return annotation(type, type, offset, line);
    }

    private static AnnotationDesc annotation(String type, String description, int offset, int line) {
        return new AnnotationDesc(offset, -1) {
            @Override
            public String getAnnotationType() {
                return type;
            }

            @Override
            public String getShortDescription() {
                return description;
            }

            @Override
            public int getOffset() {
                return offset;
            }

            @Override
            public int getLine() {
                return line;
            }
        };
    }

    @Test
    public void collectsAnnotationsOnEveryLineAndTerminates() throws Exception {
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.insertString(0, "a\nb\nc\nd\n", null);
        doc.getAnnotations().addAnnotation(annotation("first", 0, 0));
        doc.getAnnotations().addAnnotation(annotation("third", 4, 2));
        doc.getAnnotations().addAnnotation(annotation("fourth", 6, 3));

        List<GetDiagnostics.LineAnnotation> found =
                GetDiagnostics.annotationsOf(doc.getAnnotations());

        assertEquals(List.of(0, 2, 3), found.stream().map(a -> a.line).toList());
    }

    @Test
    public void emptyDocumentHasNoAnnotations() {
        BaseDocument doc = new BaseDocument(false, "text/plain");
        assertTrue(GetDiagnostics.annotationsOf(doc.getAnnotations()).isEmpty());
        assertTrue(GetDiagnostics.annotationsOf(null).isEmpty());
    }

    @Test
    public void mapsNetBeansHintTypesToSeverities() {
        String p = "org-netbeans-spi-editor-hints-parser_annotation_";
        assertEquals("error", GetDiagnostics.severityOf(p + "err"));
        assertEquals("error", GetDiagnostics.severityOf(p + "err_fixable"));
        assertEquals("warning", GetDiagnostics.severityOf(p + "warn"));
        assertEquals("hint", GetDiagnostics.severityOf(p + "verifier"));
        assertEquals("hint", GetDiagnostics.severityOf(p + "hint_fixable"));
        assertEquals("info", GetDiagnostics.severityOf("org-netbeans-modules-debugger-Breakpoint"));
        assertEquals("info", GetDiagnostics.severityOf(null));
    }

    @Test
    public void stripsNetBeansUiHelpFromMessages() {
        assertEquals("cannot find symbol\n  symbol:   variable LOGGER",
                GetDiagnostics.cleanMessage(
                        "cannot find symbol\n  symbol:   variable LOGGER\n----\n(Alt-Enter shows hints)"));
        assertEquals("plain", GetDiagnostics.cleanMessage("plain"));
        assertEquals("", GetDiagnostics.cleanMessage(null));
    }

    @Test
    public void reportsProblemsAndSkipsOtherMarkers() throws Exception {
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.insertString(0, "void main(String[] args) {}\n", null);
        GetDiagnostics tool = new GetDiagnostics();
        String p = "org-netbeans-spi-editor-hints-parser_annotation_";

        var error = tool.convertAnnotationToDiagnostic(annotation(p + "err",
                "bad main(String[] args)\n----\n(Alt-Enter shows hints)", 0, 0), doc, 0);
        assertEquals("bad main(String[] args)", error.getMessage());
        assertEquals("compiler", error.getSource());
        assertNull(error.getCode(), "no code is guessed from brackets in the message");

        assertNull(tool.convertAnnotationToDiagnostic(annotation(
                "org-netbeans-modules-java-editor-implements-annotation",
                "Implements method from : Tool", 0, 0), doc, 0));
    }

    @Test
    public void usesTheExactRangeOfAHint() throws Exception {
        var doc = new org.netbeans.modules.editor.NbEditorDocument("text/plain");
        doc.insertString(0, "class A {\n    int x = \"oops\";\n}\n", null);
        int start = doc.getText(0, doc.getLength()).indexOf("\"oops\"");

        var diagnostic = new GetDiagnostics().diagnosticFor("incompatible types",
                org.netbeans.spi.editor.hints.Severity.ERROR, start, start + 6, doc);

        assertEquals("incompatible types", diagnostic.getMessage());
        assertEquals("compiler", diagnostic.getSource());
        assertEquals(1, diagnostic.getRange().getStart().getLine());
        assertEquals(12, diagnostic.getRange().getStart().getCharacter());
        assertEquals(1, diagnostic.getRange().getEnd().getLine());
        assertEquals(18, diagnostic.getRange().getEnd().getCharacter());
    }

    @Test
    public void verifierHintsAreReportedAsHints() throws Exception {
        var doc = new org.netbeans.modules.editor.NbEditorDocument("text/plain");
        doc.insertString(0, "try {} catch (Exception e) {}\n", null);

        var diagnostic = new GetDiagnostics().diagnosticFor("Use multicatch",
                org.netbeans.spi.editor.hints.Severity.VERIFIER, 7, 26, doc);

        assertEquals("editor", diagnostic.getSource());
        assertEquals(7, diagnostic.getRange().getStart().getCharacter());
        assertEquals(26, diagnostic.getRange().getEnd().getCharacter());
    }

    @Test
    public void clampsRangesToTheDocument() throws Exception {
        var doc = new org.netbeans.modules.editor.NbEditorDocument("text/plain");
        doc.insertString(0, "abc", null);

        var diagnostic = new GetDiagnostics().diagnosticFor("x",
                org.netbeans.spi.editor.hints.Severity.WARNING, 2, 99, doc);

        assertEquals(2, diagnostic.getRange().getStart().getCharacter());
        assertEquals(3, diagnostic.getRange().getEnd().getCharacter());
    }
}

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
        return new AnnotationDesc(offset, -1) {
            @Override
            public String getAnnotationType() {
                return type;
            }

            @Override
            public String getShortDescription() {
                return type;
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
}

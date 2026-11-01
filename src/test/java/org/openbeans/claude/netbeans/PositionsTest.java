package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.JTextPane;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.Range;
import static org.junit.jupiter.api.Assertions.*;

/** Line/character positions are zero-based everywhere. */
public class PositionsTest {

    private static StyledDocument doc(String text) throws Exception {
        StyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    @Test
    public void selectionIsZeroBased() throws Exception {
        JTextPane pane = new JTextPane(doc("ab\ncdef\n"));
        pane.select(1, 5); // "b\ncd"

        NbUtils.SelectionData data = NbUtils.SelectionData.of(pane, (StyledDocument) pane.getDocument(), "/tmp/A.java");
        JsonNode json = new ObjectMapper().valueToTree(data);

        assertTrue(json.get("success").asBoolean());
        assertEquals("b\ncd", json.get("text").asText());
        assertEquals("file:///tmp/A.java", json.get("fileUrl").asText());
        assertEquals(0, json.at("/selection/start/line").asInt());
        assertEquals(1, json.at("/selection/start/character").asInt());
        assertEquals(1, json.at("/selection/end/line").asInt());
        assertEquals(2, json.at("/selection/end/character").asInt());
        assertFalse(json.at("/selection/isEmpty").asBoolean());
    }

    @Test
    public void noSelectionReportsFailure() {
        JsonNode json = new ObjectMapper().valueToTree(NbUtils.SelectionData.none("No active editor found"));
        assertFalse(json.get("success").asBoolean());
        assertEquals("No active editor found", json.get("message").asText());
        assertFalse(json.has("selection"));
    }

    @Test
    public void diagnosticRangeUsesAnnotationOffsetAndLength() throws Exception {
        StyledDocument doc = doc("int a;\nfoo bar;\n");
        Range r = org.openbeans.claude.netbeans.tools.GetDiagnosticsAccess.rangeOf(doc, 11, 3, 1); // "bar"
        assertEquals(1, r.getStart().getLine());
        assertEquals(4, r.getStart().getCharacter());
        assertEquals(1, r.getEnd().getLine());
        assertEquals(7, r.getEnd().getCharacter());
    }

    @Test
    public void wholeLineAnnotationSpansToLineEnd() throws Exception {
        StyledDocument doc = doc("int a;\nfoo bar;\n");
        Range r = org.openbeans.claude.netbeans.tools.GetDiagnosticsAccess.rangeOf(doc, 7, -1, 1);
        assertEquals(0, r.getStart().getCharacter());
        assertEquals(8, r.getEnd().getCharacter());
    }

    @Test
    public void invalidOffsetFallsBackToLineStart() throws Exception {
        Range r = org.openbeans.claude.netbeans.tools.GetDiagnosticsAccess.rangeOf(doc("x"), -1, 0, 5);
        assertEquals(5, r.getStart().getLine());
        assertEquals(0, r.getStart().getCharacter());
    }
}

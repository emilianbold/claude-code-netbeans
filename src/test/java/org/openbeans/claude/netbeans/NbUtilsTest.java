package org.openbeans.claude.netbeans;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NbUtilsTest {

    @Test
    public void selectionData_setsAllFields() {
        NbUtils.SelectionData data = new NbUtils.SelectionData("hello", "/src/Foo.java", 3, 5, 3, 10);
        assertEquals("hello", data.text);
        assertEquals("/src/Foo.java", data.filePath);
        assertEquals(3, data.startLine);
        assertEquals(5, data.startColumn);
        assertEquals(3, data.endLine);
        assertEquals(10, data.endColumn);
        assertFalse(data.isEmpty);
    }

    @Test
    public void selectionData_nullText_defaultsToEmptyString_andIsEmpty() {
        NbUtils.SelectionData data = new NbUtils.SelectionData(null, "/src/Foo.java", 0, 0, 0, 0);
        assertEquals("", data.text);
        assertTrue(data.isEmpty);
    }

    @Test
    public void selectionData_emptyText_isEmptyTrue() {
        NbUtils.SelectionData data = new NbUtils.SelectionData("", "/src/Foo.java", 1, 0, 1, 0);
        assertEquals("", data.text);
        assertTrue(data.isEmpty);
    }

    @Test
    public void selectionData_nullFilePath_isAllowed() {
        NbUtils.SelectionData data = new NbUtils.SelectionData("text", null, 1, 0, 1, 4);
        assertNull(data.filePath);
        assertFalse(data.isEmpty);
    }
}

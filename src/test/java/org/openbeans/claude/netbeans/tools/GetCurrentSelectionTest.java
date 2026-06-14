package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.GetCurrentSelectionParams;
import static org.junit.jupiter.api.Assertions.*;

public class GetCurrentSelectionTest {

    @Test
    public void run_returnsEmptySelection_whenNoEditorActive() throws Exception {
        // Without NB platform getCurrentSelectionData() returns null or throws;
        // run() handles both cases and returns an empty SelectionData.
        NbUtils.SelectionData result = new GetCurrentSelection().run(new GetCurrentSelectionParams());
        assertNotNull(result);
        assertTrue(result.isEmpty);
        assertEquals("", result.text);
    }

    @Test
    public void getName() {
        assertEquals("getCurrentSelection", new GetCurrentSelection().getName());
    }

    @Test
    public void getParameterClass() {
        assertEquals(GetCurrentSelectionParams.class, new GetCurrentSelection().getParameterClass());
    }

    @Test
    public void getDescription() {
        assertFalse(new GetCurrentSelection().getDescription().isBlank());
    }

    @Test
    public void run_returnsSelectionData_whenGetSelectionDataReturnsNonNull() throws Exception {
        NbUtils.SelectionData fakeSelection = new NbUtils.SelectionData("hello", "/test/File.java", 1, 0, 1, 5);
        GetCurrentSelection tool = new GetCurrentSelection() {
            @Override
            protected NbUtils.SelectionData getSelectionData() { return fakeSelection; }
        };

        NbUtils.SelectionData result = tool.run(new GetCurrentSelectionParams());

        assertSame(fakeSelection, result);
        assertFalse(result.isEmpty);
        assertEquals("hello", result.text);
    }

    @Test
    public void run_returnsFallback_whenGetSelectionDataThrows() throws Exception {
        GetCurrentSelection tool = new GetCurrentSelection() {
            @Override
            protected NbUtils.SelectionData getSelectionData() {
                throw new RuntimeException("simulated NB failure");
            }
        };

        NbUtils.SelectionData result = tool.run(new GetCurrentSelectionParams());

        assertNotNull(result);
        assertTrue(result.isEmpty);
    }
}

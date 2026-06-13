package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsParams;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsResult;
import static org.junit.jupiter.api.Assertions.*;

public class GetOpenEditorsTest {

    @Test
    public void run_returnsEmptyEditors_whenNBUnavailable() throws Exception {
        // TopComponent.getRegistry() fails without NB; caught by try-catch -> empty list
        GetOpenEditorsResult result = new GetOpenEditors().run(new GetOpenEditorsParams());
        assertNotNull(result);
        assertNotNull(result.getEditors());
        assertTrue(result.getEditors().isEmpty());
    }

    @Test
    public void getName() {
        assertEquals("getOpenEditors", new GetOpenEditors().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new GetOpenEditors().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(GetOpenEditorsParams.class, new GetOpenEditors().getParameterClass());
    }
}

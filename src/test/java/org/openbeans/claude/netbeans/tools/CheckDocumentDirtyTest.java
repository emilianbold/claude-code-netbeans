package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyParams;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyResult;
import static org.junit.jupiter.api.Assertions.*;

public class CheckDocumentDirtyTest {

    @Test
    public void run_returnsFalse_forPathOutsideOpenProjects() throws Exception {
        // Security check fails (no open projects or NB unavailable); caught by outer catch.
        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/tmp/test.java");
        CheckDocumentDirtyResult result = new CheckDocumentDirty().run(params);
        assertNotNull(result);
        assertFalse(result.getIsDirty());
        assertEquals("/tmp/test.java", result.getFilePath());
        assertNotNull(result.getNote());
    }

    @Test
    public void getName() {
        assertEquals("checkDocumentDirty", new CheckDocumentDirty().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new CheckDocumentDirty().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(CheckDocumentDirtyParams.class, new CheckDocumentDirty().getParameterClass());
    }

    @Test
    public void run_setsFilePath_inResult() throws Exception {
        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/tmp/another.java");
        CheckDocumentDirtyResult result = new CheckDocumentDirty().run(params);
        assertEquals("/tmp/another.java", result.getFilePath());
        assertFalse(result.getIsDirty());
    }
}

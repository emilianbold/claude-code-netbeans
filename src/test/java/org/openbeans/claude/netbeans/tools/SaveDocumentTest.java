package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentParams;
import static org.junit.jupiter.api.Assertions.*;

public class SaveDocumentTest {

    @Test
    public void getName() {
        assertEquals("saveDocument", new SaveDocument().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new SaveDocument().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(SaveDocumentParams.class, new SaveDocument().getParameterClass());
    }

    @Test
    public void run_throwsException_forPathOutsideOpenProjects() {
        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath("/tmp/test.java");
        // Security check fails (no open projects / NB unavailable) → throws
        assertThrows(Exception.class, () -> new SaveDocument().run(params));
    }
}

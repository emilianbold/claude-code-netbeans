package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.OpenFileParams;
import static org.junit.jupiter.api.Assertions.*;

public class OpenFileTest {

    @Test
    public void getName() {
        assertEquals("openFile", new OpenFile().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new OpenFile().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(OpenFileParams.class, new OpenFile().getParameterClass());
    }

    @Test
    public void run_throwsException_forPathOutsideOpenProjects() {
        OpenFileParams params = new OpenFileParams();
        params.setFilePath("/tmp/test.java");
        // Security check fails (no open projects / NB unavailable) → throws
        assertThrows(Exception.class, () -> new OpenFile().run(params));
    }
}

package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.GetDiagnosticsParams;
import static org.junit.jupiter.api.Assertions.*;

public class GetDiagnosticsTest {

    @Test
    public void run_returnsEmptyArray_whenNoUri() throws Exception {
        // null uri -> getDiagnosticsForAllFiles() -> NB exception caught -> []
        String result = new GetDiagnostics().run(new GetDiagnosticsParams());
        assertEquals("[]", result);
    }

    @Test
    public void run_returnsEmptyArray_forFileUri() throws Exception {
        // file uri -> security check fails (no open projects) or NB exception -> []
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        params.setUri("file:///nonexistent/path/Foo.java");
        String result = new GetDiagnostics().run(params);
        assertEquals("[]", result);
    }

    @Test
    public void getName() {
        assertEquals("getDiagnostics", new GetDiagnostics().getName());
    }

    @Test
    public void getParameterClass() {
        assertEquals(GetDiagnosticsParams.class, new GetDiagnostics().getParameterClass());
    }

    @Test
    public void run_returnsEmptyArray_forBarePathUri() throws Exception {
        // uri not starting with "file://" uses the raw value as filePath (ternary else branch)
        GetDiagnosticsParams params = new GetDiagnosticsParams();
        params.setUri("/tmp/some.java");
        String result = new GetDiagnostics().run(params);
        assertEquals("[]", result);
    }

    @Test
    public void getDescription() {
        assertFalse(new GetDiagnostics().getDescription().isBlank());
    }
}

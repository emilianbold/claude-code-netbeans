package org.openbeans.claude.netbeans;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NbUtilsUriTest {

    private static void assumeUnixPaths() {
        assumeTrue(java.io.File.separatorChar == '/', "POSIX path test");
    }

    @Test
    public void encodesSpecialCharacters() {
        assumeUnixPaths();
        assertEquals("file:///home/me/my%20project/A%23.java", NbUtils.toFileUri("/home/me/my project/A#.java"));
    }

    @Test
    public void decodesPercentEncodedUri() {
        assumeUnixPaths();
        assertEquals("/home/me/my project/A#.java", NbUtils.toFilePath("file:///home/me/my%20project/A%23.java"));
    }

    @Test
    public void acceptsUnencodedUri() {
        assumeUnixPaths();
        assertEquals("/home/me/my project/A.java", NbUtils.toFilePath("file:///home/me/my project/A.java"));
    }

    @Test
    public void plainPathIsUnchanged() {
        assertEquals("/tmp/x.java", NbUtils.toFilePath("/tmp/x.java"));
    }

    @Test
    public void roundTrips() {
        assumeUnixPaths();
        String path = "/tmp/ä ö/[x]/Ü.java";
        assertEquals(path, NbUtils.toFilePath(NbUtils.toFileUri(path)));
    }
}

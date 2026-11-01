package org.openbeans.claude.netbeans.tools;

import javax.swing.text.Document;
import org.openbeans.claude.netbeans.tools.params.Range;

/** Exposes package-private GetDiagnostics helpers to tests in other packages. */
public final class GetDiagnosticsAccess {
    private GetDiagnosticsAccess() {
    }

    public static Range rangeOf(Document doc, int offset, int length, int fallbackLine) {
        return GetDiagnostics.rangeOf(doc, offset, length, fallbackLine);
    }
}

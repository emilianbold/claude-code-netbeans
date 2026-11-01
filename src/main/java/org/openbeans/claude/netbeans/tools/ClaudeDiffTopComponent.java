package org.openbeans.claude.netbeans.tools;

import org.openide.windows.TopComponent;

/**
 * Window showing a diff proposed by Claude Code. A dedicated class lets
 * closeAllDiffTabs find exactly these windows without guessing from names.
 * Diffs belong to a live request, so they are never persisted across restarts.
 */
public class ClaudeDiffTopComponent extends TopComponent {

    @Override
    public int getPersistenceType() {
        return PERSISTENCE_NEVER;
    }

    @Override
    protected String preferredID() {
        return "ClaudeDiffTopComponent";
    }
}

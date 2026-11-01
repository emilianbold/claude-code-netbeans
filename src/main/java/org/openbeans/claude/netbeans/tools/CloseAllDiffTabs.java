package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.tools.params.CloseAllDiffTabsParams;
import org.openbeans.claude.netbeans.tools.params.CloseAllDiffTabsResult;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openide.util.Mutex;
import org.openide.windows.TopComponent;

public class CloseAllDiffTabs implements Tool<CloseAllDiffTabsParams, CloseAllDiffTabsResult> {

    private static final Logger LOGGER = Logger.getLogger(CloseAllDiffTabs.class.getName());

    @Override
    public String getName() {
        return "closeAllDiffTabs";
    }

    @Override
    public String getDescription() {
        return "Close all diff viewer tabs";
    }

    @Override
    public Class<CloseAllDiffTabsParams> getParameterClass() {
        return CloseAllDiffTabsParams.class;
    }

    /**
     * Closes all diff tabs opened by Claude Code. Other windows, including
     * NetBeans' own diff viewers, are left alone. Closing a pending diff
     * rejects it.
     */
    /* package protected */ int closeAllDiffTabs() {
        return Mutex.EVENT.readAccess((Mutex.Action<Integer>) () -> {
            int closedCount = 0;
            for (TopComponent tc : new ArrayList<>(TopComponent.getRegistry().getOpened())) {
                if (tc instanceof ClaudeDiffTopComponent) {
                    if (tc.close()) {
                        closedCount++;
                        LOGGER.log(Level.FINE, "Closed diff tab: {0}", tc.getDisplayName());
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to close diff tab: {0}", tc.getDisplayName());
                    }
                }
            }
            return closedCount;
        });
    }

    @Override
    public CloseAllDiffTabsResult run(CloseAllDiffTabsParams params) {
        int count = this.closeAllDiffTabs();

        // Create the content array with the status message
        List<Content> contentList = new ArrayList<>();
        Content content = new Content("text", "CLOSED_" + count + "_DIFF_TABS");
        contentList.add(content);

        return new CloseAllDiffTabsResult(contentList);
    }

}

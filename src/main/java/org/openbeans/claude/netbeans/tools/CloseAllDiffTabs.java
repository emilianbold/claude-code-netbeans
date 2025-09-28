package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.tools.params.CloseAllDiffTabsParams;
import org.openbeans.claude.netbeans.tools.params.CloseAllDiffTabsResult;
import org.openbeans.claude.netbeans.tools.params.Content;
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

    private int closeAllDiffTabs() {
        int closedCount = 0;

        // Get all open TopComponents and look for diff viewers
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            String displayName = tc.getDisplayName();
            String name = tc.getName();

            // Look for components that might be diff viewers
            // NetBeans diff viewers typically have names containing "diff" or similar patterns
            if (displayName != null && (displayName.toLowerCase().contains("diff")
                    || displayName.contains(" vs ") || displayName.contains(" - "))) {
                try {
                    tc.close();
                    closedCount++;
                    LOGGER.log(Level.FINE, "Closed diff tab: {0}", displayName);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to close diff tab: " + displayName, e);
                }
            }

            // Also check by class name for known diff viewer classes
            String className = tc.getClass().getSimpleName();
            if (className.toLowerCase().contains("diff")
                    || className.toLowerCase().contains("compare")) {
                try {
                    tc.close();
                    closedCount++;
                    LOGGER.log(Level.FINE, "Closed diff component: {0}", className);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to close diff component: " + className, e);
                }
            }
        }

        return closedCount;
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

package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;
import org.openbeans.claude.netbeans.tools.params.CloseTabResult;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Tool to close an open editor tab.
 */
public class CloseTab implements Tool<CloseTabParams, CloseTabResult> {
    
    private static final Logger LOGGER = Logger.getLogger(CloseTab.class.getName());
    
    @Override
    public String getName() {
        return "close_tab";
    }
    
    @Override
    public String getDescription() {
        return "Close an open editor tab";
    }
    
    @Override
    public Class<CloseTabParams> getParameterClass() {
        return CloseTabParams.class;
    }


    @Override
    public CloseTabResult run(CloseTabParams params) throws Exception {
        String tabName = params.getTabName();

        try {
            // Find the TopComponent by tab name
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                String displayName = tc.getDisplayName();
                if (displayName != null && displayName.equals(tabName)) {
                    // Close the tab
                    tc.close();
                    List<Content> contentList = new ArrayList<>();
                    Content content = new Content("text", "Tab closed successfully: " + tabName);
                    contentList.add(content);
                    return new CloseTabResult(contentList);
                }
            }

            // If no exact match found, try to find by file name (without path)
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                Node[] nodes = tc.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        String fileName = dataObject.getPrimaryFile().getName();
                        if (fileName.equals(tabName) || (fileName + "." + dataObject.getPrimaryFile().getExt()).equals(tabName)) {
                            tc.close();
                            List<Content> contentList = new ArrayList<>();
                            Content content = new Content("text", "Tab closed successfully: " + tabName);
                            contentList.add(content);
                            return new CloseTabResult(contentList);
                        }
                    }
                }
            }

            // If tab not found in open tabs
            LOGGER.warning("Tab not found for close request: '" + tabName + "'");
            List<Content> contentList = new ArrayList<>();
            Content content = new Content("text", "Tab not currently open: " + tabName);
            contentList.add(content);
            return new CloseTabResult(contentList);

        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
}
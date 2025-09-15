package org.openbeans.claude.netbeans.tools;

import java.util.logging.Logger;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Tool to close an open editor tab.
 */
public class CloseTab implements Tool<CloseTabParams, String> {
    
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
    public String run(CloseTabParams params) throws Exception {
        String tabName = params.getTabName();
        
        try {
            // Find the TopComponent by tab name
            for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                String displayName = tc.getDisplayName();
                if (displayName != null && displayName.equals(tabName)) {
                    // Close the tab
                    tc.close();
                    return "Tab closed successfully: " + tabName;
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
                            return "Tab closed successfully: " + tabName;
                        }
                    }
                }
            }
            
            // If tab not found in open tabs
            LOGGER.warning("Tab not found for close request: '" + tabName + "'");
            return "Tab not currently open: " + tabName;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
}
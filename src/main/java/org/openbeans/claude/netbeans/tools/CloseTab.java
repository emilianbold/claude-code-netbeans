package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;
import org.openbeans.claude.netbeans.tools.params.CloseTabResult;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Mutex;
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

    /**
     * Finds the tab to close. Claude's own diff tabs with that exact name win;
     * otherwise a tab whose display name or file name matches is used, but
     * only if exactly one does, so an ambiguous name never closes the wrong
     * file.
     *
     * @param tabName the requested tab name
     * @param opened the open windows
     * @return the tab, or null if none or several match
     */
    static TopComponent findTopComponent(String tabName, Collection<? extends TopComponent> opened) {
        for (TopComponent tc : opened) {
            if (tc instanceof ClaudeDiffTopComponent && tabName.equals(tc.getDisplayName())) {
                return tc;
            }
        }

        List<TopComponent> matches = new ArrayList<>();
        for (TopComponent tc : opened) {
            if (tabName.equals(tc.getDisplayName()) || tabName.equals(fileNameOf(tc))
                    || tabName.equals(baseNameOf(tc))) {
                matches.add(tc);
            }
        }
        if (matches.size() > 1) {
            LOGGER.warning("Tab name '" + tabName + "' is ambiguous (" + matches.size() + " tabs match); not closing any");
            return null;
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static FileObject fileOf(TopComponent tc) {
        Node[] nodes = tc.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }
        DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
        return dataObject != null ? dataObject.getPrimaryFile() : null;
    }

    private static String fileNameOf(TopComponent tc) {
        FileObject file = fileOf(tc);
        return file != null ? file.getNameExt() : null;
    }

    private static String baseNameOf(TopComponent tc) {
        FileObject file = fileOf(tc);
        return file != null ? file.getName() : null;
    }

    /* package proctected */ boolean closeTopComponent(String tabName) {
        // Window system calls must happen on the EDT
        return Mutex.EVENT.readAccess((Mutex.Action<Boolean>) () -> {
            TopComponent tc = findTopComponent(tabName, TopComponent.getRegistry().getOpened());
            if (tc != null) {
                tc.close();
                return true;
            }
            return false;
        });
    }

    @Override
    public CloseTabResult run(CloseTabParams params) throws Exception {
        String tabName = params.getTabName();

        try {
            boolean closed = closeTopComponent(tabName);
            if (closed) {
                List<Content> contentList = new ArrayList<>();
                Content content = new Content("text", "TAB_CLOSED");
                contentList.add(content);
                return new CloseTabResult(contentList);
            }

            // If tab not found in open tabs
            LOGGER.warning("Tab not found for close request: '" + tabName + "'");
            List<Content> contentList = new ArrayList<>();
            // TODO: Not sure what the proper response is when diff tab is not found. Let's go with TAB_CLOSED since maybe the user closed it?!
            Content content = new Content("text", "TAB_CLOSED");
            contentList.add(content);
            return new CloseTabResult(contentList);

        } catch (Exception e) {
            throw new RuntimeException("Failed to close tab: " + e.getMessage(), e);
        }
    }
}

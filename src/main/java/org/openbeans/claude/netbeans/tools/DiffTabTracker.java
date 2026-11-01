package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.openbeans.claude.netbeans.tools.params.Content;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;

/**
 * Tracks pending diff tabs and their async response handlers.
 *
 * Tabs are tracked by identity (not by display name), and every pending diff
 * is settled exactly once: either accepted, rejected (tab closed), or
 * superseded by a newer diff with the same tab name. Once settled, the entry
 * is removed so no second response can be sent for the same request.
 */
public final class DiffTabTracker {

    private static final Logger LOGGER = Logger.getLogger(DiffTabTracker.class.getName());

    private static final class Entry {
        final Object tab;
        final String tabName;
        final AsyncHandler<OpenDiffResult> handler;

        Entry(Object tab, String tabName, AsyncHandler<OpenDiffResult> handler) {
            this.tab = tab;
            this.tabName = tabName;
            this.handler = handler;
        }
    }

    /** Keyed by tab identity. Guarded by the class lock. */
    private static final Map<Object, Entry> pendingDiffs = new IdentityHashMap<>();

    private DiffTabTracker() {
    }

    /**
     * Registers a diff tab. Any other pending diff with the same tab name is
     * rejected (it has been superseded) and returned so the caller can close it.
     *
     * @param tab the tab component
     * @param tabName the tab name Claude Code uses to refer to the diff
     * @param handler handler to call when the diff is accepted or rejected
     * @return tabs that were superseded by this one
     */
    public static List<Object> register(Object tab, String tabName, AsyncHandler<OpenDiffResult> handler) {
        List<Entry> superseded = new ArrayList<>();
        synchronized (DiffTabTracker.class) {
            for (Iterator<Entry> it = pendingDiffs.values().iterator(); it.hasNext();) {
                Entry e = it.next();
                if (e.tabName.equals(tabName)) {
                    it.remove();
                    superseded.add(e);
                }
            }
            pendingDiffs.put(tab, new Entry(tab, tabName, handler));
        }
        LOGGER.fine("Registered diff tab: " + tabName);

        List<Object> supersededTabs = new ArrayList<>();
        for (Entry e : superseded) {
            LOGGER.info("Diff tab superseded by a newer diff with the same name: " + tabName);
            e.handler.sendResponse(rejectedResult(e.tabName));
            supersededTabs.add(e.tab);
        }
        return supersededTabs;
    }

    /**
     * Accepts the pending diff for the given tab.
     *
     * @param tab the tab component
     * @param result the result to send
     * @return true if a pending diff was accepted, false if it was already settled
     */
    public static boolean accept(Object tab, OpenDiffResult result) {
        Entry e = take(tab);
        if (e == null) {
            LOGGER.fine("Ignoring accept for a diff that is no longer pending");
            return false;
        }
        e.handler.sendResponse(result);
        return true;
    }

    /**
     * Rejects the pending diff for the given tab (e.g. because it was closed).
     *
     * @param tab the tab component
     * @return true if a pending diff was rejected, false if it was already settled
     */
    public static boolean reject(Object tab) {
        Entry e = take(tab);
        if (e == null) {
            return false;
        }
        LOGGER.info("Diff rejected: " + e.tabName);
        e.handler.sendResponse(rejectedResult(e.tabName));
        return true;
    }

    /**
     * @param tab the tab component
     * @return true if the tab has an unsettled diff
     */
    public static synchronized boolean isPending(Object tab) {
        return pendingDiffs.containsKey(tab);
    }

    private static synchronized Entry take(Object tab) {
        return pendingDiffs.remove(tab);
    }

    /** For tests only. */
    static synchronized void clear() {
        pendingDiffs.clear();
    }

    static OpenDiffResult rejectedResult(String tabName) {
        List<Content> contentList = new ArrayList<>();
        contentList.add(new Content("text", "DIFF_REJECTED"));
        contentList.add(new Content("text", tabName));
        return new OpenDiffResult(contentList);
    }
}

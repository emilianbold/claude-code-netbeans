package org.openbeans.claude.netbeans.tools;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks pending diff tabs and their async response handlers.
 * When a diff tab is closed or accepted, the corresponding handler is notified.
 */
public class DiffTabTracker {

    private static final Logger LOGGER = Logger.getLogger(DiffTabTracker.class.getName());

    private static final ConcurrentHashMap<String, AsyncHandler> pendingDiffs = new ConcurrentHashMap<>();

    /**
     * Register a diff tab for async response handling.
     * @param tabName The name of the diff tab
     * @param handler Handler to call when the diff is accepted or rejected
     */
    public static void register(String tabName, AsyncHandler handler) {
        LOGGER.info("Registering async handler for diff tab: " + tabName);
        pendingDiffs.put(tabName, handler);
    }

    /**
     * Get and remove the handler for a diff tab.
     * @param tabName The name of the diff tab
     * @return The async handler, or null if not found
     */
    public static AsyncHandler remove(String tabName) {
        AsyncHandler handler = pendingDiffs.remove(tabName);
        if (handler != null) {
            LOGGER.info("Removed async handler for diff tab: " + tabName);
        }
        return handler;
    }

    /**
     * Check if a tab name is tracked as a pending diff.
     * @param tabName The name of the tab
     * @return true if this is a tracked diff tab
     */
    public static boolean isTracked(String tabName) {
        return pendingDiffs.containsKey(tabName);
    }
}

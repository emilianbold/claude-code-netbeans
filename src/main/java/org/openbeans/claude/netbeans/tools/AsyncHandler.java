package org.openbeans.claude.netbeans.tools;

/**
 * Handler for sending asynchronous tool responses.
 * Provided to async tools so they can send responses later.
 */
public interface AsyncHandler<O> {
    /**
     * Send the final response for an async tool call.
     * @param result The result object to serialize and send
     */
    void sendResponse(O result);
}

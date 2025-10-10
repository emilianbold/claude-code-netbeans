package org.openbeans.claude.netbeans.tools;

/**
 * Marker interface for tools that respond asynchronously.
 * Tools returning this type will not send an immediate response.
 * The handler will be called later when the async operation completes.
 */
public interface AsyncResponse<O> {
    /**
     * Called by the MCP handler to provide a callback for sending the delayed response.
     * @param handler Handler that will send the response when ready
     */
    void setHandler(AsyncHandler<O> handler);
}

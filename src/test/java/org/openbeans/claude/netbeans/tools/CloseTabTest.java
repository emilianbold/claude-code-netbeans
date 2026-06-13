package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;

public class CloseTabTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MCPResponseBuilder responseBuilder = new MCPResponseBuilder(objectMapper);

    @Test
    public void testJSONResponse() throws JsonProcessingException, Exception {
        CloseTab tool = new CloseTab() {
            @Override
            boolean closeTopComponent(String tabName) {
                return true;
            }
        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseTabParams("a name")));

        // Note the full JSON response is: { "result" : n, "jsonrpc" : "2.0","id" : <someID> }
        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"TAB_CLOSED\"}]}", objectMapper.writeValueAsString(n));
    }

    @Test
    public void testJSONResponse_tabNotFound() throws Exception {
        CloseTab tool = new CloseTab() {
            @Override
            boolean closeTopComponent(String tabName) {
                return false;
            }
        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseTabParams("missing tab")));

        // TAB_CLOSED is returned even when the tab is not found (user may have already closed it)
        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"TAB_CLOSED\"}]}", objectMapper.writeValueAsString(n));
    }
}

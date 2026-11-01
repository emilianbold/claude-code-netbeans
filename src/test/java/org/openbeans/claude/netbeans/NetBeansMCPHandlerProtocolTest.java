package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.AsyncResponse;
import org.openbeans.claude.netbeans.tools.Tool;
import org.openbeans.claude.netbeans.tools.params.CloseTabResult;
import org.openbeans.claude.netbeans.tools.params.Content;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON-RPC 2.0 / MCP conformance of NetBeansMCPHandler, using fake tools so
 * no NetBeans window system is needed.
 */
public class NetBeansMCPHandlerProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    public static class EchoParams {
        public String value;
    }

    public static class Pojo {
        public String name = "x";
        public int count = 3;
    }

    private abstract static class FakeTool<O> implements Tool<EchoParams, O> {
        private final String name;

        FakeTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public Class<EchoParams> getParameterClass() {
            return EchoParams.class;
        }
    }

    /** Captures messages the handler pushes over the socket. */
    private static class TestHandler extends NetBeansMCPHandler {
        final List<String> sent = new ArrayList<>();

        TestHandler(List<Tool<?, ?>> tools) {
            super(tools);
        }

        @Override
        void send(String message) {
            sent.add(message);
        }
    }

    private TestHandler handler() {
        List<Tool<?, ?>> tools = new ArrayList<>();
        tools.add(new FakeTool<String>("text") {
            @Override
            public String run(EchoParams p) {
                return "hello " + p.value;
            }
        });
        tools.add(new FakeTool<Pojo>("pojo") {
            @Override
            public Pojo run(EchoParams p) {
                return new Pojo();
            }
        });
        tools.add(new FakeTool<CloseTabResult>("content") {
            @Override
            public CloseTabResult run(EchoParams p) {
                return new CloseTabResult(List.of(new Content("text", "TAB_CLOSED")));
            }
        });
        tools.add(new FakeTool<String>("fails") {
            @Override
            public String run(EchoParams p) {
                throw new IllegalStateException("boom");
            }
        });
        tools.add(new FakeTool<AsyncResponse<Pojo>>("async") {
            @Override
            public AsyncResponse<Pojo> run(EchoParams p) {
                return h -> h.sendResponse(new Pojo());
            }
        });
        return new TestHandler(tools);
    }

    private JsonNode call(NetBeansMCPHandler h, String json) throws Exception {
        String response = h.handleMessage(mapper.readTree(json));
        return response == null ? null : mapper.readTree(response);
    }

    // --- ids ---

    @Test
    public void stringIdIsEchoedVerbatim() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":\"abc-1\",\"method\":\"ping\"}");
        assertTrue(r.get("id").isTextual());
        assertEquals("abc-1", r.get("id").asText());
    }

    @Test
    public void numericIdIsEchoedVerbatim() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");
        assertEquals(7, r.get("id").asInt());
        assertTrue(r.get("result").isObject());
    }

    // --- notifications ---

    @Test
    public void initializedNotificationGetsNoResponse() throws Exception {
        assertNull(call(handler(), "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    public void unknownNotificationGetsNoResponse() throws Exception {
        assertNull(call(handler(), "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/whatever\"}"));
    }

    @Test
    public void failingNotificationGetsNoResponse() throws Exception {
        assertNull(call(handler(), "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\"}"));
    }

    @Test
    public void responsesFromClientAreIgnored() throws Exception {
        assertNull(call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{}}"));
    }

    // --- errors ---

    @Test
    public void unknownMethodIsMethodNotFoundWithId() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"nope\"}");
        assertEquals(-32601, r.get("error").get("code").asInt());
        assertEquals(3, r.get("id").asInt());
    }

    @Test
    public void requestWithoutMethodIsInvalidRequest() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":4}");
        assertEquals(-32600, r.get("error").get("code").asInt());
        assertEquals(4, r.get("id").asInt());
    }

    @Test
    public void internalErrorKeepsId() throws Exception {
        // tools/call without params blows up inside the handler
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"method\":\"tools/call\"}");
        assertNotNull(r.get("error"));
        assertEquals("x", r.get("id").asText());
    }

    @Test
    public void unknownToolIsInvalidParams() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}");
        assertEquals(-32602, r.get("error").get("code").asInt());
    }

    @Test
    public void malformedArgumentsAreInvalidParams() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"text\",\"arguments\":{\"value\":{\"nested\":true}}}}");
        assertEquals(-32602, r.get("error").get("code").asInt());
    }

    // --- initialize / capabilities ---

    @Test
    public void initializeDoesNotPushServerSideInitializedNotification() throws Exception {
        TestHandler h = handler();
        JsonNode r = call(h, "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}");
        assertNotNull(r.get("result"));
        assertTrue(h.sent.isEmpty(), "server must not send notifications/initialized: " + h.sent);
    }

    @Test
    public void onlyImplementedCapabilitiesAreAdvertised() throws Exception {
        JsonNode caps = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}")
                .get("result").get("capabilities");
        assertTrue(caps.has("tools"));
        assertFalse(caps.path("tools").path("listChanged").asBoolean(false));
        assertFalse(caps.path("resources").path("subscribe").asBoolean(false));
        assertFalse(caps.path("resources").path("listChanged").asBoolean(false));
        assertFalse(caps.has("prompts"), "prompts/get is not implemented");
    }

    // --- tool results ---

    private JsonNode toolResult(TestHandler h, String tool, String args) throws Exception {
        JsonNode r = call(h, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + tool + "\",\"arguments\":" + args + "}}");
        assertNull(r.get("error"), String.valueOf(r));
        return r.get("result");
    }

    @Test
    public void stringResultIsWrappedInContent() throws Exception {
        JsonNode result = toolResult(handler(), "text", "{\"value\":\"world\"}");
        assertEquals("text", result.get("content").get(0).get("type").asText());
        assertEquals("hello world", result.get("content").get(0).get("text").asText());
        assertFalse(result.path("isError").asBoolean(false));
    }

    @Test
    public void objectResultIsSerializedAsJsonText() throws Exception {
        JsonNode result = toolResult(handler(), "pojo", "{}");
        JsonNode payload = mapper.readTree(result.get("content").get(0).get("text").asText());
        assertEquals("x", payload.get("name").asText());
        assertEquals(3, payload.get("count").asInt());
    }

    @Test
    public void contentResultIsPassedThrough() throws Exception {
        JsonNode result = toolResult(handler(), "content", "{}");
        assertEquals("TAB_CLOSED", result.get("content").get(0).get("text").asText());
        assertEquals(1, result.get("content").size());
    }

    @Test
    public void toolFailureSetsIsError() throws Exception {
        JsonNode result = toolResult(handler(), "fails", "{}");
        assertTrue(result.get("isError").asBoolean());
        assertTrue(result.get("content").get(0).get("text").asText().contains("boom"));
    }

    @Test
    public void unknownArgumentsAreIgnored() throws Exception {
        JsonNode result = toolResult(handler(), "text", "{\"value\":\"a\",\"makeFrontmost\":true}");
        assertEquals("hello a", result.get("content").get(0).get("text").asText());
    }

    @Test
    public void missingArgumentsAreTreatedAsEmpty() throws Exception {
        JsonNode r = call(handler(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"pojo\"}}");
        assertNotNull(r.get("result").get("content"));
    }

    @Test
    public void asyncResultUsesOriginalIdAndContentFormat() throws Exception {
        TestHandler h = handler();
        assertNull(call(h, "{\"jsonrpc\":\"2.0\",\"id\":\"req-9\",\"method\":\"tools/call\",\"params\":{\"name\":\"async\"}}"));
        assertEquals(1, h.sent.size());
        JsonNode r = mapper.readTree(h.sent.get(0));
        assertEquals("req-9", r.get("id").asText());
        assertNotNull(r.get("result").get("content"));
    }
}

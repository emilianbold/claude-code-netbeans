package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface Tool<T, O> {

    String getName();

    String getDescription();

    /**
     * Returns the parameter class type for JSON deserialization.
     * Implementations should return the actual class of their parameter type.
     */
    Class<T> getParameterClass();

    /**
     * Converts the tool call arguments to the parameter class. Missing
     * arguments are treated as an empty object, and unknown properties are
     * ignored so newer Claude Code versions sending extra fields keep working.
     *
     * @param arguments the "arguments" of the tools/call request, may be null
     * @return the parsed parameters, never null
     * @throws IllegalArgumentException if the arguments cannot be converted
     */
    default T parseArguments(JsonNode arguments) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode args = (arguments == null || arguments.isNull()) ? mapper.createObjectNode() : arguments;
        return mapper.convertValue(args, getParameterClass());
    }

    /**
     * Execute the tool with the given parameters.
     * @param params Parsed tool parameters
     * @return Either the result object (for sync tools) or an AsyncResponse (for async tools)
     * @throws Exception if the tool execution fails
     */
    O run(T params) throws Exception;
}

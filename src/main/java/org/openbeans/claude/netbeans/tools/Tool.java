package org.openbeans.claude.netbeans.tools;

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

    default T parseArguments(JsonNode arguments) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(arguments, getParameterClass());
    }

    /**
     * Execute the tool with the given parameters.
     * @param params Parsed tool parameters
     * @return Either the result object (for sync tools) or an AsyncResponse (for async tools)
     * @throws Exception if the tool execution fails
     */
    O run(T params) throws Exception;
}

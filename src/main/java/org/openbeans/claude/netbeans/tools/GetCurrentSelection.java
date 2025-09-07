package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.GetCurrentSelectionParams;

/**
 * Tool to get the current text selection in the active editor.
 */
public class GetCurrentSelection implements Tool<GetCurrentSelectionParams> {
    
    private static final Logger LOGGER = Logger.getLogger(GetCurrentSelection.class.getName());
    
    @Override
    public String getName() {
        return "getCurrentSelection";
    }
    
    @Override
    public String getDescription() {
        return "Get the current text selection in the active editor";
    }
    
    @Override
    public Class<GetCurrentSelectionParams> getParameterClass() {
        return GetCurrentSelectionParams.class;
    }
    
    @Override
    public JsonNode run(GetCurrentSelectionParams params, MCPResponseBuilder responseBuilder) throws Exception {
        try {
            NbUtils.SelectionData selectionData = NbUtils.getCurrentSelectionData();
            if (selectionData != null) {
                return buildSelectionResponse(selectionData, responseBuilder);
            } else {
                return responseBuilder.createToolResponse("");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current selection", e);
            return responseBuilder.createToolResponse("");
        }
    }
    
    /**
     * Builds JSON response for selection data.
     * 
     * @param selectionData the selection data to convert to JSON response
     * @param responseBuilder the response builder
     * @return JSON response for the selection
     */
    private JsonNode buildSelectionResponse(NbUtils.SelectionData selectionData, MCPResponseBuilder responseBuilder) {
        if (selectionData.isEmpty) {
            return responseBuilder.createToolResponse("");
        }
        
        // Let Jackson automatically serialize the SelectionData object
        return responseBuilder.createToolResponse(selectionData);
    }
}
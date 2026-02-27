package org.chappiebot.assist;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.chappiebot.rag.RagRequestContext;
import org.chappiebot.store.StoreManager;

/**
 * The Endpoint for dynamic queries.
 * Accepts AI assistant requests and returns generated responses.
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/assist")
public class AssistantEndpoint {

    private static final int MAX_MEMORY_ID_LENGTH = 100;

    @Inject
    Assistant dynamicAssistant;

    @Inject
    RagRequestContext ragRequestContext;

    @Inject
    StoreManager storeManager;

    @POST
    public Response assist(
            @Valid AssistInput input,
            @HeaderParam(HEADER_MEMORY_ID) String memoryId) {
        
            if (memoryId == null || memoryId.isBlank()) {
                memoryId = UUID.randomUUID().toString();
            } else if (memoryId.length() > MAX_MEMORY_ID_LENGTH) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Memory ID must not exceed " + MAX_MEMORY_ID_LENGTH + " characters"))
                    .build();
            }
        
            ragRequestContext.setVariables(input.genericInput().variables());
            
            String responseSchema = input.responseSchemaPrompt();
            if(responseSchema == null)responseSchema ="";
            
            Map<String,Object> r = dynamicAssistant.assist(input.genericInput().programmingLanguage(),
                        input.genericInput().programmingLanguageVersion(),
                        input.genericInput().quarkusVersion(),
                        input.genericInput().getSystemMessage(), 
                        input.genericInput().getUserMessage(),
                        responseSchema,
                        memoryId);
            
            if(r.containsKey(NICE_NAME)){
                String niceName = String.valueOf(r.get(NICE_NAME));
                if(storeManager.getJdbcChatMemoryStore().isPresent() && niceName!=null && !niceName.isBlank()){
                    storeManager.getJdbcChatMemoryStore().get().setNiceName(memoryId, niceName);
                }
            }
            
            return Response
                    .ok(r)
                    .header(HEADER_MEMORY_ID, memoryId)
                    .build();
    }
    
    private static final String NICE_NAME = "niceName";
    private static final String HEADER_MEMORY_ID = "X-Chappie-MemoryId";
}
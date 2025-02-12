package org.chappiebot.content.generation;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for content generation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/generate")
public class GenerationEndpoint {
    
    @Inject
    GenerationAssistant generationAssistant;
    
    @POST
    public Uni<GenerationOutput> generate(GenerationInput input) {
        return Uni.createFrom().item(() -> generationAssistant.generate(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.path(), 
                input.content(), 
                input.genericInput().systemmessage(), 
                input.genericInput().usermessage()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
    
}

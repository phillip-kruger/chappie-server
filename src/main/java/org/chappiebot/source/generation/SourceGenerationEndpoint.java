package org.chappiebot.source.generation;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for source generation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/generateSource")
public class SourceGenerationEndpoint {
    
    @Inject
    SourceGenerationAssistant sourceGenerationAssistant;
    
    @POST
    public Uni<SourceGenerationOutput> generateSource(SourceGenerationInput input) {
        return Uni.createFrom().item(() -> sourceGenerationAssistant.generateSource(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.source(), 
                input.genericInput().systemmessage(), 
                input.genericInput().usermessage()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
    
}

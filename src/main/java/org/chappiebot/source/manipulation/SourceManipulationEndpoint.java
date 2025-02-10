package org.chappiebot.source.manipulation;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for source manipulation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/manipulateSource")
public class SourceManipulationEndpoint {
    
    @Inject
    SourceManipulationAssistant sourceManipulationAssistant;
    
    @POST
    public Uni<SourceManipulationOutput> manipulateSource(SourceManipulationInput input) {
        return Uni.createFrom().item(() -> sourceManipulationAssistant.manipulateSource(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.source(), 
                input.genericInput().systemmessage(), 
                input.genericInput().usermessage()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
    
}

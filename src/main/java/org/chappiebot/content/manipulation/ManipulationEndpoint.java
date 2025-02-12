package org.chappiebot.content.manipulation;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for content manipulation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/manipulate")
public class ManipulationEndpoint {
    
    @Inject
    ManipulationAssistant manipulationAssistant;
    
    @POST
    public Uni<ManipulationOutput> manipulate(ManipulationInput input) {
        return Uni.createFrom().item(() -> manipulationAssistant.manipulate(input.genericInput().programmingLanguage(),
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

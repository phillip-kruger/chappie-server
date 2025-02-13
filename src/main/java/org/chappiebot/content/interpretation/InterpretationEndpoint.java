package org.chappiebot.content.interpretation;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for content interpretation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/interpret")
public class InterpretationEndpoint {
    
    @Inject
    InterpretationAssistant interpretationAssistant;
    
    @POST
    public Uni<InterpretationOutput> interpret(InterpretationInput input) {
        return Uni.createFrom().item(() -> interpretationAssistant.interpret(input.genericInput().programmingLanguage(),
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

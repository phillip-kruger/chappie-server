package org.chappiebot.content.interpretation;

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
    public InterpretationOutput interpret(InterpretationInput input) {
        return interpretationAssistant.interpret(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.path(), 
                input.content(), 
                input.genericInput().systemmessage(), 
                input.genericInput().usermessage());
    }
    
}

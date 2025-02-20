package org.chappiebot.content.manipulation;

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
    public ManipulationOutput manipulate(ManipulationInput input) {
        return  manipulationAssistant.manipulate(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.path(), 
                input.content(), 
                input.genericInput().systemmessage(), 
                input.genericInput().usermessage());
    }
    
}

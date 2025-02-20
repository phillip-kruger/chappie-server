package org.chappiebot.dynamic;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;

/**
 * The Endpoint for dynamic queries
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/dynamic")
public class DynamicEndpoint {
    
    @Inject
    DynamicAssistant dynamicAssistant;
    
    @POST
    public DynamicOutput dynamic(DynamicInput input) {
       
        Log.info(input.variables());
        
        String sm = replacePlaceholders(input.genericInput().systemmessage(),input.variables());
        String um = replacePlaceholders(input.genericInput().usermessage(), input.variables());

        Log.info(sm);
        Log.info(um);
        return dynamicAssistant.dynamic(input.genericInput().programmingLanguage(),
            input.genericInput().programmingLanguageVersion(),
            input.genericInput().product(),
            input.genericInput().productVersion(),
            sm, 
            um);
    }
    
    
    // TODO: There must be a Langchain4J way to do this ?
    private String replacePlaceholders(String template, Map<String, String> variables) {
        String result = template;
        if(variables!=null && !variables.isEmpty()){
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }

}

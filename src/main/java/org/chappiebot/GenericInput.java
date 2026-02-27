package org.chappiebot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Generic input for AI assistant requests.
 *
 * @param programmingLanguage The programming language context
 * @param programmingLanguageVersion The version of the programming language
 * @param quarkusVersion The Quarkus version context
 * @param systemmessageTemplate The system message template (max 50000 chars)
 * @param usermessageTemplate The user message template (required, max 50000 chars)
 * @param variables Variables to substitute in templates
 */
public record GenericInput(
    @Size(max = 50, message = "programmingLanguage must not exceed 50 characters")
    String programmingLanguage,

    @Size(max = 20, message = "programmingLanguageVersion must not exceed 20 characters")
    String programmingLanguageVersion,

    @Size(max = 20, message = "quarkusVersion must not exceed 20 characters")
    String quarkusVersion,

    @Size(max = 50000, message = "systemmessageTemplate must not exceed 50000 characters")
    String systemmessageTemplate,

    @NotBlank(message = "usermessageTemplate is required")
    @Size(max = 50000, message = "usermessageTemplate must not exceed 50000 characters")
    String usermessageTemplate,

    Map<String, String> variables
) {

    public String getUserMessage() {
        return getMessage(usermessageTemplate, variables);
    }
    
    public String getSystemMessage() {
        return getMessage(systemmessageTemplate, variables);
    }

    // TODO: There must be a Langchain4J way to do this ?    
    private String getMessage(String result, Map<String, String> variables) {
        if(variables!=null && !variables.isEmpty()){
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }
    
}

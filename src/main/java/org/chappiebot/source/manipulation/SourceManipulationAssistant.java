package org.chappiebot.source.manipulation;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SourceManipulationAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in {{programmingLanguage}} {{programmingLanguageVersion}} code from a {{product}} {{productVersion}} application.
                You will receive code that needs to me manipulated. Use the code received as input when considering the response.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with the manipulated source code. This response must be valid {{programmingLanguage}}. Only include the {{programmingLanguage}} code, no explanation or other text. 
                
                You must not wrap {{programmingLanguage}} response in backticks, markdown, or in any other way, but return it as plain text.
                
                {{systemmessage}}
            """;
    
    static final String USER_MESSAGE = """
        I have the following {{programmingLanguage}} class:
                    ```
                    {{source}}
                    ```
                    
                    {{usermessage}} 
                """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage(USER_MESSAGE)
    public SourceManipulationOutput manipulateSource(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version, 
                        @V("source")String source,
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}

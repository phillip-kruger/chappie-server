package org.chappiebot.content.generation;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface GenerationAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in {{programmingLanguage}} {{programmingLanguageVersion}} code from a {{product}} {{productVersion}} application.
                You will receive content that needs to used as base for other content generation. Use the content received as input when considering the response. 
                Also consider the path of the content to determine the file type of the provided content.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with the generated content. This response must be valid content in the content type requested. Only include the generated content, no explanation or other text. 
                
                You must not wrap generated content response in backticks, markdown, or in any other way, but return it as plain text.
                
                {{systemmessage}}
            """;
    
    static final String USER_MESSAGE = """
        I have the following content, store under this path: {{path}}, in this {{product}} project:
                    ```
                    {{content}}
                    ```
                    
                    {{usermessage}} 
                """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage(USER_MESSAGE)
    public GenerationOutput generate(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version, 
                        @V("path")String path,
                        @V("content")String content,
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}

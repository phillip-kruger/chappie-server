package org.chappiebot.content.interpretation;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterpretationAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in {{programmingLanguage}} {{programmingLanguageVersion}} code from a {{product}} {{productVersion}} application.
                You will receive content that needs to be interpreted . Use the content received as input when considering the response. 
                Also consider the path of the content to determine the file type of the provided content.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with an interpretation in markdown format, but make sure this markdown in encoded such that it can be added to a json file. This response must be valid markdown. Only include the markdown content, no explanation or other text. 
                
                You must not wrap markdown content in backticks, or in any other way, but return it as plain markdown encoded for json. If the interpretation contains code, make sure to use the markdown format to display the code properly.
                
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
    public InterpretationOutput interpret(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version,
                        @V("path")String path,
                        @V("content")String content,
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}

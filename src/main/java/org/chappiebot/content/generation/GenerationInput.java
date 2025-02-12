package org.chappiebot.content.generation;

import org.chappiebot.GenericInput;

public record GenerationInput(GenericInput genericInput,
                        String path,
                        String content){
}

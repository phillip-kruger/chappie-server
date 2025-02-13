package org.chappiebot.content.interpretation;

import org.chappiebot.GenericInput;

public record InterpretationInput(GenericInput genericInput,
                        String path,
                        String content){
}

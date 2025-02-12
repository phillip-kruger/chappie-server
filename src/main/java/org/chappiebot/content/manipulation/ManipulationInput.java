package org.chappiebot.content.manipulation;

import org.chappiebot.GenericInput;

public record ManipulationInput(GenericInput genericInput,
                        String path,
                        String content){
}

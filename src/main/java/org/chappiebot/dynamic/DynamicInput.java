package org.chappiebot.dynamic;

import java.util.Map;
import org.chappiebot.GenericInput;

public record DynamicInput(GenericInput genericInput,
                        Map<String, String> variables){
}

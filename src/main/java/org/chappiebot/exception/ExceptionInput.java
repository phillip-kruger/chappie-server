package org.chappiebot.exception;

import org.chappiebot.GenericInput;

public record ExceptionInput(GenericInput genericInput,
                        String stacktrace,
                        String path,
                        String content){
}

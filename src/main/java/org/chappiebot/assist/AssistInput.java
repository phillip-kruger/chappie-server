package org.chappiebot.assist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.file.Path;
import java.util.List;
import org.chappiebot.GenericInput;

/**
 * Input for the assist endpoint.
 *
 * @param genericInput The core input parameters (required)
 * @param paths Optional list of file paths for context
 * @param responseSchemaPrompt Optional schema for structured responses (max 10000 chars)
 */
public record AssistInput(
    @NotNull(message = "genericInput is required")
    @Valid
    GenericInput genericInput,

    List<Path> paths,

    @Size(max = 10000, message = "responseSchemaPrompt must not exceed 10000 characters")
    String responseSchemaPrompt
) {
}

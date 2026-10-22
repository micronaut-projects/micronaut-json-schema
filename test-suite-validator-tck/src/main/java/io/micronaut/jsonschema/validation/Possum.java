package io.micronaut.jsonschema.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.micronaut.jsonschema.JsonSchema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * A possum.
 *
 * @param name The name
 * @param children The children
 * @param environment The environment
 */
@JsonSchema
public record Possum(
        @NotBlank
        @JsonInclude(Include.NON_NULL)
        String name,
        List<Possum> children,
        Environment environment) {
}

package io.micronaut.jsonschema.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A possum.
 *
 * @param name The name
 * @param children The children
 * @param environment The environment
 */
@JsonSchema
@Serdeable
public record Possum(
    @NotBlank
    @JsonInclude(Include.NON_NULL)
    String name,
    List<Possum> children,
    Environment environment
) {

    /**
     * The environment.
     *
     * @param name The name
     */
    @JsonSchema
    @Serdeable
    public record Environment(
        @Size(min = 2) String name
    ) {
    }

}

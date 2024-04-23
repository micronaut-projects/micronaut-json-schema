package io.micronaut.jsonschema.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A llama. <4>
 *
 * @param name The name
 * @param age The age
 */
@JsonSchema // <1>
@Serdeable // <2>
public record Llama(
    @NotBlank // <3>
    @JsonInclude(Include.NON_NULL)
    String name,
    @PositiveOrZero // <3>
    int age
) {
}

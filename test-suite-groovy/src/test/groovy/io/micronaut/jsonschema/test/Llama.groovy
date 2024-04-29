package io.micronaut.jsonschema.test

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import io.micronaut.jsonschema.JsonSchema
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

/**
 * A llama. <4>
 */
@JsonSchema // <1>
@Serdeable // <2>
class Llama {
    /**
     * The name.
     */
    @NotBlank // <3>
    @JsonInclude(Include.NON_NULL)
    String name

    /**
     * The age.
     */
    @PositiveOrZero // <3>
    Integer age
}

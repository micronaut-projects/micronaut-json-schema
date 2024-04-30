package io.micronaut.jsonschema.validation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.Min;

/**
 * The eagle.
 *
 * @param name The name
 * @param flySpeed The fly speed
 */
@JsonTypeName("eagle-bird")
record Eagle(
        String name,
        @Min(1)
        Float flySpeed
) implements Bird {
}

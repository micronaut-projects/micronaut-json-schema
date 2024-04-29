package io.micronaut.jsonschema.validation;

import jakarta.validation.constraints.Positive;

/**
 * An ostrich.
 *
 * @param name The name
 * @param runSpeed The run speed
 */
record Ostrich(
        String name,
        @Positive
        Float runSpeed
) implements Bird {
}

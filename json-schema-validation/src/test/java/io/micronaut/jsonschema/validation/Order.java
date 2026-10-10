package io.micronaut.jsonschema.validation;

import io.micronaut.jsonschema.JsonSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * An order, which references the schema of a nested type.
 *
 * @param lines The lines
 */
@JsonSchema
public record Order(
    List<Line> lines
) {

    /**
     * A line of an order.
     *
     * @param product The product
     * @param quantity The quantity
     */
    @JsonSchema
    public record Line(
        @NotBlank
        String product,
        @Positive
        int quantity
    ) {
    }
}

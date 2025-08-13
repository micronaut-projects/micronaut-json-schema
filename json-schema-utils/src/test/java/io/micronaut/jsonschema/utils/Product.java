package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

import java.math.BigDecimal;

/**
 *
 * @param product The price of the product
 */
@JsonSchema
@Introspected
public record Product(BigDecimal product) {
}

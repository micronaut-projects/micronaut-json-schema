package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(embedded = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"title\":\"Embedded Generated Product\",\"type\":\"object\",\"properties\":{\"product\":{\"type\":\"string\"}}}")
@Introspected
public record EmbeddedGeneratedProduct(String product) {
}

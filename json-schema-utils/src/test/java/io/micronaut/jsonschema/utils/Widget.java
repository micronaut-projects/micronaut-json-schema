package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "FancyWidget")
@Introspected
public record Widget(String name) {
}

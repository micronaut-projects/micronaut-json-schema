package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(uri = "/shop/gadget")
@Introspected
public record Gadget(String name) {
}

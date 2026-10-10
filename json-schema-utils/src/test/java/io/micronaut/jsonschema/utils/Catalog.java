package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

import java.util.List;

@JsonSchema
@Introspected
public record Catalog(List<Item> items, Bin bin) {

    @JsonSchema
    @Introspected
    public record Item(String name) {
    }

    /**
     * A nested type without an introspection, whose name the loader reads with reflection.
     */
    @JsonSchema(title = "StorageBin")
    public record Bin(String label) {
    }
}

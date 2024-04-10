package io.micronaut.jsonschema.validation;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.JsonSchema;

/**
 * A red-winged blackbird.
 *
 * @param name The name
 * @param wingSpan The wing span of the bird
 */
// tag::clazz[]
@JsonSchema(
    title = "RedWingedBlackbird", // <1>
    description = "A species of blackbird with red wings",
    uri = "/red-winged-blackbird" // <2>
)
@Introspected
public record RWBlackbird(
    String name,
    double wingSpan
) {
}
// end::clazz[]

package io.micronaut.jsonschema.test;

import io.micronaut.jsonschema.JsonSchemaConfiguration;

/**
 * A configuration.
 */
// tag::clazz[]
@JsonSchemaConfiguration(
    baseUri = "https://example.com/schemas" // <1>
)
public interface JsonSchemaConfig {
}
// end::clazz[]

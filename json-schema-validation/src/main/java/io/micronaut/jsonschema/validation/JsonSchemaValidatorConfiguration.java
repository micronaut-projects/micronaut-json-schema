package io.micronaut.jsonschema.validation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * A configuration for {@link JsonSchemaValidator}.
 * The validator will resolve URIs that start with base URI to JSON schemas in the specified
 * folder on the classpath.
 *
 * @param baseUri The base URI for JSON schemas to be validated
 * @param classpathFolder THe folder where the JSON schemas are located, on the classpath
 *
 * @author Andriy Dmytruk
 * @since 1.0.0
 */
@ConfigurationProperties(JsonSchemaValidatorConfiguration.PREFIX)
public record JsonSchemaValidatorConfiguration(
    @Bindable(defaultValue = "http://localhost:8080/schemas/")
    String baseUri,
    @Bindable(defaultValue = "META-INF/schemas/")
    String classpathFolder
) {

    public static final String PREFIX = "micronaut.jsonschema.validation";

}

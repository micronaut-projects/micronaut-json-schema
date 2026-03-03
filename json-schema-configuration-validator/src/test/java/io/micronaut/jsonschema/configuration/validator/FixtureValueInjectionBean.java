package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;

/**
 * Test fixture bean that exercises missing required {@code @Value} property validation.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureValueInjectionBean {
    @Value("${spec.missing.value}")
    String value;
}

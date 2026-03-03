package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

/**
 * Test fixture bean that exercises required constructor injection validation.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureConstructorInjectionBean {
    FixtureConstructorInjectionBean(FixtureMissingDependency missingDependency) {
    }
}

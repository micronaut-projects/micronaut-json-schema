package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Test fixture bean that exercises required field injection validation.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureFieldInjectionBean {
    @Inject
    FixtureMissingDependency missingDependency;
}

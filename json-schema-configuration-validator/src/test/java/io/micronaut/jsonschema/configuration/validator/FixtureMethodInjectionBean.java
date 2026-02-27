package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Test fixture bean that exercises required method injection validation.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureMethodInjectionBean {
    @Inject
    void inject(FixtureMissingDependency missingDependency) {
    }
}

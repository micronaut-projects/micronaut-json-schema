package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;

/**
 * Test fixture bean used to validate qualifier-aware dependency resolution metadata.
 */
@Context
@Requires(property = "spec.name", value = "qualifier")
@Requires(env = "di-validator-test")
public final class FixtureQualifierInjectionBean {
    FixtureQualifierInjectionBean(@Named("primary") FixtureMissingDependency missingDependency) {
    }
}

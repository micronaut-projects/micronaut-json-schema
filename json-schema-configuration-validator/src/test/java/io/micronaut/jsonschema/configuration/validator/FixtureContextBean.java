package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

/**
 * Test fixture bean used to validate context bean constructor dependency checks.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureContextBean {
    FixtureContextBean(FixtureMissingDependency missingDependency) {
    }
}

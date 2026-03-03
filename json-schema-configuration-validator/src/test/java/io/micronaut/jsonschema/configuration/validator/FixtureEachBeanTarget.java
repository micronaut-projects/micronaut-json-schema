package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

/**
 * Test fixture bean created per {@link FixtureEachConfig} entry to validate each-bean injection.
 */
@EachBean(FixtureEachConfig.class)
@Context
@Requires(env = "di-validator-test")
public final class FixtureEachBeanTarget {
    FixtureEachBeanTarget(FixtureMissingDependency missingDependency) {
    }
}

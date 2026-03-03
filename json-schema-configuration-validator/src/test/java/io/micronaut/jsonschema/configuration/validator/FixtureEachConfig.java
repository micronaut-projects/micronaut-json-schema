package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

/**
 * Test fixture each-property configuration used as a source bean for {@code @EachBean} tests.
 */
@EachProperty("spec.each")
@Requires(env = "di-validator-test")
public final class FixtureEachConfig {
    FixtureEachConfig(@Parameter String name) {
    }
}

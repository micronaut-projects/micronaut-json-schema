package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

/**
 * Fixture bean produced per datasource configuration entry.
 */
@EachBean(FixtureDataSourceConfiguration.class)
@Requires(env = "di-validator-test")
public final class FixtureDataSourceBean implements FixtureDataSource {
    FixtureDataSourceBean(FixtureDataSourceConfiguration configuration) {
    }
}

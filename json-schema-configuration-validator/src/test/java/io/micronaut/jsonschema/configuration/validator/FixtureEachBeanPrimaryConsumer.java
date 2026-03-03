package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Fixture consumer that injects the primary datasource bean produced from each-property config.
 */
@Singleton
@Requires(env = "di-validator-test")
public final class FixtureEachBeanPrimaryConsumer {
    FixtureEachBeanPrimaryConsumer(FixtureDataSource dataSource) {
    }

    @Executable(processOnStartup = true)
    @SuppressWarnings("java:S1186")
    void entrypoint() {
    }
}

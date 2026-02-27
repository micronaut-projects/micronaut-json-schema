package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Fixture consumer that injects a named datasource bean produced from each-property config.
 */
@Singleton
@Requires(env = "di-validator-test")
public final class FixtureEachBeanNamedConsumer {
    FixtureEachBeanNamedConsumer(@Named("other") FixtureDataSource dataSource) {
    }

    @Executable(processOnStartup = true)
    void entrypoint() {
    }
}

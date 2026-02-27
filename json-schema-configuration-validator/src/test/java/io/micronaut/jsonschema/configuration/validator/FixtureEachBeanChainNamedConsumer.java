package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Requires(env = "di-validator-test")
@Requires(property = "spec.name", value = "eachbean-chain-named")
public final class FixtureEachBeanChainNamedConsumer {
    FixtureEachBeanChainNamedConsumer(@Named("other") FixtureDataSourceConfigurer configurer) {
    }

    @Executable(processOnStartup = true)
    void entrypoint() {
    }
}

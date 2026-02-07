package io.micronaut.jsonschema.test;

import io.micronaut.jsonschema.configuration.validator.ConfigurationErrors;
import jakarta.inject.Singleton;

@Singleton
class ConfigurationErrorsExample {

    private final ConfigurationErrors configurationErrors;

    ConfigurationErrorsExample(ConfigurationErrors configurationErrors) {
        this.configurationErrors = configurationErrors;
    }

    // tag::runtime-api[]
    void logConfigurationErrors() {
        configurationErrors.getCurrentErrors().forEach(System.out::println);
    }
    // end::runtime-api[]
}

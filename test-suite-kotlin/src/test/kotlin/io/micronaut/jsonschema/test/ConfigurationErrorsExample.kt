package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.configuration.validator.ConfigurationErrors
import jakarta.inject.Singleton

@Singleton
class ConfigurationErrorsExample(private val configurationErrors: ConfigurationErrors) {

    // tag::runtime-api[]
    fun logConfigurationErrors() {
        configurationErrors.currentErrors.forEach(::println)
    }
    // end::runtime-api[]
}

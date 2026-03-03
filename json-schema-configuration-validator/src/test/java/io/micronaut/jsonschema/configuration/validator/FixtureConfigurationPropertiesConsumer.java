package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "configuration-properties-record")
@Requires(env = "di-validator-test")
final class FixtureConfigurationPropertiesConsumer {
    FixtureConfigurationPropertiesConsumer(FixtureConfigurationPropertiesRecord configuration) {
    }
}

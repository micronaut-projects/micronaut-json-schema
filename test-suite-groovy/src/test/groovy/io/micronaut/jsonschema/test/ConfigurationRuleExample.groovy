package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.configuration.validator.ConfigurationError
import io.micronaut.jsonschema.configuration.validator.ConfigurationRule
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidationContext

class ConfigurationRuleExample implements ConfigurationRule {

    // tag::rule[]

    @Override
    boolean supportsPrefix(String prefix) {
        prefix == 'jpa.default.properties'
    }

    @Override
    Set<ConfigurationError> validate(ConfigurationValidationContext context) {
        Set<ConfigurationError> errors = new LinkedHashSet<>()

        if (!context.environment().containsProperty('datasources.default.url')) {
            errors.add(new ConfigurationError(
                    'datasources.default.url',
                    'Required when jpa.default.properties is set',
                    null,
                    null,
                    null
            ))
        }

        return errors
    }
    // end::rule[]
}

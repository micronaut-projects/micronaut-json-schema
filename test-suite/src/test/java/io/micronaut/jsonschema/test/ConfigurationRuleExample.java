package io.micronaut.jsonschema.test;

import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.ConfigurationRule;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidationContext;

import java.util.LinkedHashSet;
import java.util.Set;

class ConfigurationRuleExample implements ConfigurationRule {

    // tag::rule[]

    @Override
    public boolean supportsPrefix(String prefix) {
        return "jpa.default.properties".equals(prefix);
    }

    @Override
    public Set<ConfigurationError> validate(ConfigurationValidationContext context) {
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        if (!context.environment().containsProperty("datasources.default.url")) {
            errors.add(new ConfigurationError(
                "datasources.default.url",
                "Required when jpa.default.properties is set",
                null,
                null,
                null
            ));
        }

        return errors;
    }
    // end::rule[]
}

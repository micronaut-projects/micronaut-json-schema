package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.configuration.validator.ConfigurationError
import io.micronaut.jsonschema.configuration.validator.ConfigurationRule
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidationContext
import java.util.LinkedHashSet

class ConfigurationRuleExample : ConfigurationRule {

    // tag::rule[]
    override fun validate(context: ConfigurationValidationContext): Set<ConfigurationError> {
        val errors = LinkedHashSet<ConfigurationError>()

        if (context.environment.containsProperties("jpa.default.properties")
            && !context.environment.containsProperty("datasources.default.url")
        ) {
            errors.add(
                ConfigurationError(
                    "datasources.default.url",
                    "Required when jpa.default.properties is set",
                    null,
                    null,
                    null
                )
            )
        }

        return errors
    }
    // end::rule[]
}

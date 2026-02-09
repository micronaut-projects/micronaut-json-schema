package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.configuration.validator.ConfigurationError
import io.micronaut.jsonschema.configuration.validator.ConfigurationRule
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidationContext
import java.util.LinkedHashSet

class ConfigurationRuleExample : ConfigurationRule {

    // tag::rule[]

    override fun supportsPrefix(prefix: String): Boolean = prefix == "jpa.default.properties"

    override fun validate(context: ConfigurationValidationContext): Set<ConfigurationError> {
        val errors = LinkedHashSet<ConfigurationError>()

        if (!context.environment.containsProperty("datasources.default.url")) {
            errors.add(
                ConfigurationError.builder(
                    "datasources.default.url",
                    "Required when jpa.default.properties is set"
                ).build()
            )
        }

        return errors
    }
    // end::rule[]
}

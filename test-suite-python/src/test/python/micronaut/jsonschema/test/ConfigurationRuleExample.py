from micronaut.jsonschema.configuration.validator import (
    ConfigurationError,
    ConfigurationRule,
    ConfigurationValidationContext,
)


class ConfigurationRuleExample(ConfigurationRule):

    # tag::rule[]
    def supportsPrefix(self, prefix: str) -> bool:
        return prefix == "jpa.default.properties"

    def validate(self, context: ConfigurationValidationContext) -> set[ConfigurationError]:
        errors = set()

        if not context.environment().containsProperty("datasources.default.url"):
            errors.add(ConfigurationError.builder(
                "datasources.default.url",
                "Required when jpa.default.properties is set"
            ).build())

        return errors
    # end::rule[]

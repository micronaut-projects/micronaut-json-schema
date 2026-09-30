from jakarta.inject import Singleton
from micronaut.jsonschema.configuration.validator import ConfigurationErrors


@Singleton
class ConfigurationErrorsExample:

    def __init__(self, configuration_errors: ConfigurationErrors):
        self.configuration_errors = configuration_errors

    # tag::runtime-api[]
    def log_configuration_errors(self) -> None:
        for error in self.configuration_errors.getCurrentErrors():
            print(error)
    # end::runtime-api[]

package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "factory-method-missing-arg")
@Requires(env = "di-validator-test")
final class FixtureFactoryMethodMissingArgConsumer {

    FixtureFactoryMethodMissingArgConsumer(FixtureFactoryMethodMissingArgGreeter greeter) {
    }
}

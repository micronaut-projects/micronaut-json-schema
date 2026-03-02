package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "factory-method-missing-arg")
@Requires(env = "di-validator-test")
final class FixtureFactoryMethodMissingArgFactory {

    @Singleton
    FixtureFactoryMethodMissingArgGreeter greeter(String str) {
        return new FixtureFactoryMethodMissingArgGreeter();
    }
}

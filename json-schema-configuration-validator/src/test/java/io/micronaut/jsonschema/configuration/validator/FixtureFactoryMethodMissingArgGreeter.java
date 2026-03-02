package io.micronaut.jsonschema.configuration.validator;

final class FixtureFactoryMethodMissingArgGreeter {

    String greet(String name) {
        return "HELLO " + name;
    }
}

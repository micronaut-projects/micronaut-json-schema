package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(env = "di-validator-test")
@Requires(property = "spec.name", value = "conditional-single")
public final class FixtureConditionalSingleConsumer {
    FixtureConditionalSingleConsumer(FixtureConditionalService service) {
    }
}

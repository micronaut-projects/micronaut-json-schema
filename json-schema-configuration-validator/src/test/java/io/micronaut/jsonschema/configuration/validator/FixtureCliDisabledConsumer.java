package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(env = "di-validator-test")
public final class FixtureCliDisabledConsumer {
    FixtureCliDisabledConsumer(FixtureCliDisabledService service) {
    }
}

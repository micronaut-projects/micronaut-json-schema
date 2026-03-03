package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "non-unique-order")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidateOrderConsumer {
    FixtureMultipleCandidateOrderConsumer(FixtureMultipleCandidateService service) {
    }
}

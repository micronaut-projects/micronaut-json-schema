package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "non-unique-secondary")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidateSecondaryConsumer {
    FixtureMultipleCandidateSecondaryConsumer(FixtureMultipleCandidateService service) {
    }
}

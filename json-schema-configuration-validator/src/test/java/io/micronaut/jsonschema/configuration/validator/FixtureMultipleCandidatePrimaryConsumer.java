package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "spec.name", value = "non-unique-primary")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidatePrimaryConsumer {
    FixtureMultipleCandidatePrimaryConsumer(FixtureMultipleCandidateService service) {
    }
}

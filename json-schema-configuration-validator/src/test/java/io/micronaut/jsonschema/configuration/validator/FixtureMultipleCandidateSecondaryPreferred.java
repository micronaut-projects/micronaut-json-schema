package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "non-unique-secondary")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidateSecondaryPreferred implements FixtureMultipleCandidateService {
}

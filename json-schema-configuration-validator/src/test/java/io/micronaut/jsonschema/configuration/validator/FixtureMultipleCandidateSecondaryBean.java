package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

@Secondary
@Singleton
@Requires(property = "spec.name", value = "non-unique-secondary")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidateSecondaryBean implements FixtureMultipleCandidateService {
}

package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Primary
@Singleton
@Requires(property = "spec.name", value = "non-unique-primary")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidatePrimaryBean implements FixtureMultipleCandidateService {
}

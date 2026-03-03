package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(env = "di-validator-test")
@Requires(property = "spec.name", value = "conditional-multi")
@Requires(property = "spec.conditional.enabled", value = "true")
public final class FixtureConditionalPropertyRequiredCandidate implements FixtureConditionalService {
}

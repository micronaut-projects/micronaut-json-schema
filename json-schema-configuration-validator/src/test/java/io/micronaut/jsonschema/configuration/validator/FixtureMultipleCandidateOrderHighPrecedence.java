package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Singleton
@Order(10)
@Requires(property = "spec.name", value = "non-unique-order")
@Requires(env = "di-validator-test")
final class FixtureMultipleCandidateOrderHighPrecedence implements FixtureMultipleCandidateService {
}

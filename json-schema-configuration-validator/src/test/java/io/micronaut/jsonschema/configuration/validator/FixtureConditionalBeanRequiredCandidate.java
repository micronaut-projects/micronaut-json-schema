package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(env = "di-validator-test")
@Requires(property = "spec.name", value = "conditional-single")
@Requires(bean = FixtureConditionalDependency.class)
public final class FixtureConditionalBeanRequiredCandidate implements FixtureConditionalService {
}

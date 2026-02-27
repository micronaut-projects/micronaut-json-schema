package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(env = "di-validator-test")
@Requires(property = "spec.cli.disabled.enabled", value = "true")
public final class FixtureCliDisabledCandidateProperty implements FixtureCliDisabledService {
}

package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("fixture.reactor")
@Requires(property = "spec.name", value = "configuration-properties-record")
@Requires(env = "di-validator-test")
record FixtureConfigurationPropertiesRecord(
    @Nullable Boolean enableAutomaticContextPropagation,
    @Nullable Boolean enableScheduleHookContextPropagation
) {
}

package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;

/**
 * Test fixture bean used to validate scheduled entry-point dependency checks.
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureScheduledBean {
    @Inject
    FixtureMissingDependency missingDependency;

    @Scheduled(fixedDelay = "10m")
    void run() {
    }
}

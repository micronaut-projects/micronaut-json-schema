package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;

/**
 * Test fixture startup listener used to validate event-listener root dependency checks.
 */
@Singleton
@Requires(env = "di-validator-test")
public final class FixtureStartupListenerBean implements ApplicationEventListener<ServerStartupEvent> {
    FixtureStartupListenerBean(FixtureMissingDependency missingDependency) {
    }

    @Override
    @SuppressWarnings("java:S1186")
    public void onApplicationEvent(ServerStartupEvent event) {
    }
}

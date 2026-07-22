/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Runs registry reconciliation on application startup.
 *
 * @since 2.2.0
 */
@Singleton
@Requires(property = JsonSchemaRegistryConfiguration.PREFIX + ".enabled", value = "true")
public final class JsonSchemaRegistryStartupListener implements ApplicationEventListener<ServerStartupEvent> {
    private final JsonSchemaRegistryConfiguration configuration;
    private final JsonSchemaRegistryService service;

    /**
     * @param configuration Registry configuration
     * @param service Registry service
     */
    public JsonSchemaRegistryStartupListener(JsonSchemaRegistryConfiguration configuration,
                                             JsonSchemaRegistryService service) {
        this.configuration = configuration;
        this.service = service;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        List<JsonSchemaRegistryOutcome> outcomes = service.resync();
        if (configuration.getFailFastStrategy() == JsonSchemaRegistryFailFastStrategy.STARTUP_ABORT
            && outcomes.stream().anyMatch(JsonSchemaRegistryOutcome::failure)) {
            throw new JsonSchemaRegistryException("JSON Schema Registry startup reconciliation failed");
        }
    }
}

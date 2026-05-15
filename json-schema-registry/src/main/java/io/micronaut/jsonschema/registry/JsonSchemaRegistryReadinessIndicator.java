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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Readiness;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

/**
 * Readiness indicator backed by the last registry reconciliation state.
 *
 * @since 2.0.0
 */
@Readiness
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(property = JsonSchemaRegistryConfiguration.PREFIX + ".enabled", value = "true")
public final class JsonSchemaRegistryReadinessIndicator implements HealthIndicator {
    static final String NAME = "json-schema-registry";

    private final JsonSchemaRegistryConfiguration configuration;
    private final JsonSchemaRegistryState state;

    /**
     * @param configuration Registry configuration
     * @param state Registry state
     */
    public JsonSchemaRegistryReadinessIndicator(JsonSchemaRegistryConfiguration configuration,
                                                JsonSchemaRegistryState state) {
        this.configuration = configuration;
        this.state = state;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        HealthStatus status = state.ready(configuration) ? HealthStatus.UP : HealthStatus.DOWN;
        return Publishers.just(HealthResult.builder(NAME, status)
            .details(state.details())
            .build());
    }
}

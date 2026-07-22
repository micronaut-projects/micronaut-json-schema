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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default programmatic registry reconciliation service.
 *
 * @since 2.2.0
 */
@Singleton
public final class DefaultJsonSchemaRegistryService implements JsonSchemaRegistryService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaRegistryService.class);

    private final JsonSchemaRegistryConfiguration configuration;
    private final JsonSchemaRegistryReconciler reconciler;
    private final JsonSchemaRegistryObservability observability;
    private final JsonSchemaRegistryState state;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param configuration Registry configuration
     * @param reconciler Registry reconciler
     * @param observability Registry observability
     * @param state Registry state
     */
    public DefaultJsonSchemaRegistryService(JsonSchemaRegistryConfiguration configuration,
                                            JsonSchemaRegistryReconciler reconciler,
                                            JsonSchemaRegistryObservability observability,
                                            JsonSchemaRegistryState state) {
        this.configuration = configuration;
        this.reconciler = reconciler;
        this.observability = observability;
        this.state = state;
    }

    @Override
    public List<JsonSchemaRegistryOutcome> resync() {
        if (!running.compareAndSet(false, true)) {
            throw new JsonSchemaRegistryException("JSON Schema Registry reconciliation is already running");
        }
        state.running();
        long started = System.nanoTime();
        try (JsonSchemaRegistryRunContext ignored = JsonSchemaRegistryRunContext.open(UUID.randomUUID().toString())) {
            try {
                List<JsonSchemaRegistryOutcome> outcomes = reconciler.reconcile();
                Duration duration = Duration.ofNanos(System.nanoTime() - started);
                state.completed(duration, outcomes);
                observability.record(configuration, duration, outcomes);
                if (outcomes.stream().anyMatch(JsonSchemaRegistryOutcome::failure)) {
                    logFailure();
                }
                return outcomes;
            } catch (RuntimeException e) {
                Duration duration = Duration.ofNanos(System.nanoTime() - started);
                List<JsonSchemaRegistryOutcome> outcomes = List.of(JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("registry", null, null),
                    "registry",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    e.getMessage()
                ));
                state.completed(duration, outcomes);
                observability.record(configuration, duration, outcomes);
                logFailure(e);
                return outcomes;
            }
        } finally {
            running.set(false);
        }
    }

    private void logFailure() {
        switch (configuration.getFailFastStrategy()) {
            case NONE -> LOG.warn("JSON Schema Registry reconciliation failed; continuing because fail-fast-strategy=none");
            case READINESS_GATE -> LOG.error("JSON Schema Registry reconciliation failed; readiness will remain DOWN");
            case STARTUP_ABORT -> LOG.error("JSON Schema Registry reconciliation failed; startup listener will abort startup if this is a startup run");
            default -> throw new IllegalStateException("Unknown fail-fast strategy: " + configuration.getFailFastStrategy());
        }
    }

    private void logFailure(RuntimeException e) {
        switch (configuration.getFailFastStrategy()) {
            case NONE -> LOG.warn("JSON Schema Registry reconciliation failed; continuing because fail-fast-strategy=none", e);
            case READINESS_GATE -> LOG.error("JSON Schema Registry reconciliation failed; readiness will remain DOWN", e);
            case STARTUP_ABORT -> LOG.error("JSON Schema Registry reconciliation failed; startup listener will abort startup if this is a startup run", e);
            default -> throw new IllegalStateException("Unknown fail-fast strategy: " + configuration.getFailFastStrategy(), e);
        }
    }
}

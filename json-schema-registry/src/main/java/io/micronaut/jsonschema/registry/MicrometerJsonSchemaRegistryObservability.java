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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;

/**
 * Micrometer-backed registry reconciliation observability.
 *
 * @since 2.0.0
 */
@Singleton
@Replaces(DefaultJsonSchemaRegistryObservability.class)
@Requires(classes = MeterRegistry.class)
@Requires(beans = MeterRegistry.class)
public final class MicrometerJsonSchemaRegistryObservability implements JsonSchemaRegistryObservability {
    private static final String OUTCOME_COUNTER = "json.schema.registry.outcomes";
    private static final String DISCOVERED_COUNTER = "json.schema.registry.discovered.schemas";
    private static final String DURATION_TIMER = "json.schema.registry.reconcile.duration";

    private final MeterRegistry meterRegistry;
    private final DefaultJsonSchemaRegistryObservability loggingObservability = new DefaultJsonSchemaRegistryObservability();

    /**
     * @param meterRegistry Micrometer meter registry
     */
    public MicrometerJsonSchemaRegistryObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void record(JsonSchemaRegistryConfiguration configuration,
                       Duration duration,
                       List<JsonSchemaRegistryOutcome> outcomes) {
        loggingObservability.record(configuration, duration, outcomes);
        boolean failure = outcomes.stream().anyMatch(JsonSchemaRegistryOutcome::failure);
        for (JsonSchemaRegistryOutcome outcome : outcomes) {
            recordOutcome(configuration, outcome);
        }
        recordDiscovered(configuration, outcomes);
        Timer.builder(DURATION_TIMER)
            .description("JSON Schema Registry reconciliation run duration")
            .tag("authority", DefaultJsonSchemaRegistryObservability.tagValue(configuration.getAuthority()))
            .tag("dry_run", Boolean.toString(configuration.isDryRun()))
            .tag("failure", Boolean.toString(failure))
            .register(meterRegistry)
            .record(duration);
    }

    private void recordOutcome(JsonSchemaRegistryConfiguration configuration,
                               JsonSchemaRegistryOutcome outcome) {
        Counter.builder(OUTCOME_COUNTER)
            .description("JSON Schema Registry reconciliation outcomes")
            .tag("target", DefaultJsonSchemaRegistryObservability.targetTag(outcome.target()))
            .tag("authority", DefaultJsonSchemaRegistryObservability.tagValue(configuration.getAuthority()))
            .tag("mode", DefaultJsonSchemaRegistryObservability.mode(configuration, outcome.target()))
            .tag("result", DefaultJsonSchemaRegistryObservability.tagValue(outcome.status()))
            .tag("failure", Boolean.toString(outcome.failure()))
            .register(meterRegistry)
            .increment();
    }

    private void recordDiscovered(JsonSchemaRegistryConfiguration configuration,
                                  List<JsonSchemaRegistryOutcome> outcomes) {
        long discovered = outcomes.stream()
            .filter(outcome -> outcome.target() != null && outcome.target().endsWith(".authority"))
            .filter(outcome -> outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT)
            .count();
        if (discovered == 0) {
            return;
        }
        Counter.builder(DISCOVERED_COUNTER)
            .description("JSON Schema Registry authority schemas discovered successfully")
            .tag("authority", DefaultJsonSchemaRegistryObservability.tagValue(configuration.getAuthority()))
            .register(meterRegistry)
            .increment(discovered);
    }
}

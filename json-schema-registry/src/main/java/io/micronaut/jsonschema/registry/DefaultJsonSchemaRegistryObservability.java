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
import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default registry reconciliation observability.
 *
 * @since 2.0.0
 */
@Singleton
public final class DefaultJsonSchemaRegistryObservability implements JsonSchemaRegistryObservability {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaRegistryObservability.class);
    private static final String OUTCOME_COUNTER = "json.schema.registry.outcomes";
    private static final String DURATION_TIMER = "json.schema.registry.reconcile.duration";

    private final Optional<MeterRegistry> meterRegistry;

    /**
     * @param beanContext Bean context
     */
    public DefaultJsonSchemaRegistryObservability(BeanContext beanContext) {
        this.meterRegistry = beanContext.findBean(MeterRegistry.class);
    }

    @Override
    public void record(JsonSchemaRegistryConfiguration configuration,
                       Duration duration,
                       List<JsonSchemaRegistryOutcome> outcomes) {
        boolean failure = outcomes.stream().anyMatch(JsonSchemaRegistryOutcome::failure);
        for (JsonSchemaRegistryOutcome outcome : outcomes) {
            logOutcome(configuration, outcome);
            meterRegistry.ifPresent(meterRegistry -> recordOutcome(meterRegistry, configuration, outcome));
        }
        LOG.info("{}JSON Schema Registry reconciliation summary: authority={} dryRun={} failFastStrategy={} outcomes={} failures={} durationMs={} results={}",
            dryRunPrefix(configuration),
            tagValue(configuration.getAuthority()),
            configuration.isDryRun(),
            tagValue(configuration.getFailFastStrategy()),
            outcomes.size(),
            outcomes.stream().filter(JsonSchemaRegistryOutcome::failure).count(),
            duration.toMillis(),
            summarize(outcomes));
        meterRegistry.ifPresent(meterRegistry -> Timer.builder(DURATION_TIMER)
            .description("JSON Schema Registry reconciliation run duration")
            .tag("authority", tagValue(configuration.getAuthority()))
            .tag("dry_run", Boolean.toString(configuration.isDryRun()))
            .tag("failure", Boolean.toString(failure))
            .register(meterRegistry)
            .record(duration));
    }

    private static void logOutcome(JsonSchemaRegistryConfiguration configuration,
                                   JsonSchemaRegistryOutcome outcome) {
        if (outcome.failure()) {
            LOG.warn("{}JSON Schema Registry reconciliation outcome: authority={} target={} mode={} result={} failure={} logicalSchema={} message={}",
                dryRunPrefix(configuration),
                tagValue(configuration.getAuthority()),
                outcome.target(),
                mode(configuration, outcome.target()),
                tagValue(outcome.status()),
                outcome.failure(),
                outcome.logicalSchema().logicalFqcn(),
                outcome.message());
        } else {
            LOG.info("{}JSON Schema Registry reconciliation outcome: authority={} target={} mode={} result={} failure={} logicalSchema={} message={}",
                dryRunPrefix(configuration),
                tagValue(configuration.getAuthority()),
                outcome.target(),
                mode(configuration, outcome.target()),
                tagValue(outcome.status()),
                outcome.failure(),
                outcome.logicalSchema().logicalFqcn(),
                outcome.message());
        }
    }

    private static void recordOutcome(MeterRegistry meterRegistry,
                                      JsonSchemaRegistryConfiguration configuration,
                                      JsonSchemaRegistryOutcome outcome) {
        Counter.builder(OUTCOME_COUNTER)
            .description("JSON Schema Registry reconciliation outcomes")
            .tag("target", targetTag(outcome.target()))
            .tag("authority", tagValue(configuration.getAuthority()))
            .tag("mode", mode(configuration, outcome.target()))
            .tag("result", tagValue(outcome.status()))
            .tag("failure", Boolean.toString(outcome.failure()))
            .register(meterRegistry)
            .increment();
    }

    private static String dryRunPrefix(JsonSchemaRegistryConfiguration configuration) {
        return configuration.isDryRun() ? "[DRY-RUN] " : "";
    }

    private static Map<String, Long> summarize(List<JsonSchemaRegistryOutcome> outcomes) {
        return outcomes.stream()
            .collect(Collectors.groupingBy(outcome -> tagValue(outcome.status()), Collectors.counting()));
    }

    private static String mode(JsonSchemaRegistryConfiguration configuration, String target) {
        return switch (targetTag(target)) {
            case "sr" -> tagValue(configuration.getSr().getPolicy().getMode());
            case "oracle" -> tagValue(configuration.getOracle().getPolicy().getMode());
            default -> "authority";
        };
    }

    private static String targetTag(String target) {
        if (target == null || target.isBlank()) {
            return "registry";
        }
        if (target.startsWith("sr")) {
            return "sr";
        }
        if (target.startsWith("oracle")) {
            return "oracle";
        }
        if (target.startsWith("application")) {
            return "application";
        }
        return target.toLowerCase(Locale.ENGLISH);
    }

    private static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ENGLISH);
    }
}

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

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Last known registry reconciliation state.
 *
 * @since 2.0.0
 */
@Singleton
public final class JsonSchemaRegistryState {
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.notRun());

    /**
     * Mark reconciliation as running.
     */
    public void running() {
        snapshot.set(new Snapshot(RunStatus.RUNNING, Instant.now(), Duration.ZERO, List.of(), null));
    }

    /**
     * Mark reconciliation as completed.
     *
     * @param duration Run duration
     * @param outcomes Reconciliation outcomes
     */
    public void completed(Duration duration, List<JsonSchemaRegistryOutcome> outcomes) {
        List<JsonSchemaRegistryOutcome> copy = List.copyOf(outcomes);
        RunStatus status = copy.stream().anyMatch(JsonSchemaRegistryOutcome::failure) ? RunStatus.FAILED : RunStatus.SUCCESS;
        snapshot.set(new Snapshot(status, Instant.now(), duration, copy, null));
    }

    /**
     * Mark reconciliation as failed before outcomes were available.
     *
     * @param duration Run duration
     * @param exception Failure
     */
    public void failed(Duration duration, Exception exception) {
        snapshot.set(new Snapshot(RunStatus.FAILED, Instant.now(), duration, List.of(), exception.getMessage()));
    }

    /**
     * @param configuration Registry configuration
     * @return Whether readiness should report UP
     */
    public boolean ready(JsonSchemaRegistryConfiguration configuration) {
        if (!configuration.isEnabled() || !configuration.isFailFast()) {
            return true;
        }
        return snapshot.get().status() == RunStatus.SUCCESS;
    }

    /**
     * @return Health/details representation
     */
    public Map<String, Object> details() {
        Snapshot current = snapshot.get();
        long failures = current.outcomes().stream().filter(JsonSchemaRegistryOutcome::failure).count();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", current.status().name().toLowerCase(java.util.Locale.ENGLISH));
        details.put("lastUpdated", current.lastUpdated().toString());
        details.put("durationMs", current.duration().toMillis());
        details.put("outcomes", current.outcomes().size());
        details.put("failures", failures);
        List<String> failureMessages = current.outcomes().stream()
            .filter(JsonSchemaRegistryOutcome::failure)
            .map(JsonSchemaRegistryOutcome::message)
            .filter(message -> message != null && !message.isBlank())
            .limit(5)
            .collect(Collectors.toList());
        if (!failureMessages.isEmpty()) {
            details.put("failureMessages", failureMessages);
        }
        if (current.error() != null) {
            details.put("error", current.error());
        }
        return Map.copyOf(details);
    }

    /**
     * @return Last snapshot
     */
    Snapshot snapshot() {
        return snapshot.get();
    }

    enum RunStatus {
        NOT_RUN,
        RUNNING,
        SUCCESS,
        FAILED
    }

    record Snapshot(
        RunStatus status,
        Instant lastUpdated,
        Duration duration,
        List<JsonSchemaRegistryOutcome> outcomes,
        String error
    ) {
        static Snapshot notRun() {
            return new Snapshot(RunStatus.NOT_RUN, Instant.EPOCH, Duration.ZERO, List.of(), null);
        }
    }
}

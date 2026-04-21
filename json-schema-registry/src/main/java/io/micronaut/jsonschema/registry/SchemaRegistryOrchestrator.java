/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.registry.api.RegistryTarget;
import io.micronaut.jsonschema.registry.api.RegistryTargetDiff;
import io.micronaut.jsonschema.registry.canon.SchemaCanonicalizer;
import io.micronaut.jsonschema.registry.discovery.SchemaDiscoveryService;
import io.micronaut.jsonschema.registry.model.GeneratedSchema;
import io.micronaut.jsonschema.registry.naming.SubjectStrategy;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates startup-time synchronization of generated JSON Schemas to configured targets.
 *
 * This is a minimal stub implementation intended to establish the integration points.
 *
 * @since 1.0.0
 */
@Internal
@Singleton
final class SchemaRegistryOrchestrator implements ApplicationEventListener<StartupEvent> {

    private final JsonSchemaRegistryConfiguration configuration;
    private final SchemaDiscoveryService discoveryService;
    private final List<RegistryTarget> targets;
    private final SubjectStrategy subjectStrategy;
    private final SchemaCanonicalizer canonicalizer;

    SchemaRegistryOrchestrator(JsonSchemaRegistryConfiguration configuration,
                               SchemaDiscoveryService discoveryService,
                               List<RegistryTarget> targets,
                               SubjectStrategy subjectStrategy,
                               SchemaCanonicalizer canonicalizer) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.subjectStrategy = Objects.requireNonNull(subjectStrategy, "subjectStrategy");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    @Override
    public void onApplicationEvent(@NonNull StartupEvent event) {
        if (!configuration.isEnabled()) {
            return;
        }
        List<GeneratedSchema> schemas = discoveryService.discover();
        for (GeneratedSchema schema : schemas) {
            // Ensure canonicalization exists (stub: assume already canonicalized when produced by discovery).
            // In a future iteration, discovery will return raw JSON and we canonicalize here if needed.
            String subject = subjectStrategy.subjectFor(schema);
            for (RegistryTarget target : targets) {
                try {
                    RegistryTargetDiff diff = target.diff(schema);
                    switch (diff.getAction()) {
                        case NONE -> { /* no-op */ }
                        case CREATE, UPDATE -> {
                            if (!configuration.isDryRun()) {
                                target.apply(schema);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (configuration.isFailFast()) {
                        // Propagate to fail startup
                        throw new IllegalStateException("Failed to synchronize schema subject '" + subject + "' to target '" + target.id() + "'", e);
                    }
                    // best-effort: continue with next target
                }
            }
        }
    }
}

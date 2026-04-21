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
package io.micronaut.jsonschema.registry.api;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.registry.model.GeneratedSchema;

/**
 * A target that can receive JSON Schemas (e.g. Confluent Schema Registry, Oracle 23ai Domains).
 * Implementations should be side-effect free in {@link #diff(GeneratedSchema)} and perform changes in {@link #apply(GeneratedSchema)}.
 *
 * @since 1.0.0
 */
@Internal
public interface RegistryTarget {

    /**
     * A stable identifier for this target (e.g. "confluent", "oracle").
     * @return id
     */
    @NonNull
    String id();

    /**
     * Compute the difference between the current state of the target and the provided schema.
     * Must not perform any changes.
     * @param schema schema to compare
     * @return a diff describing actions to take
     */
    @NonNull
    RegistryTargetDiff diff(@NonNull GeneratedSchema schema);

    /**
     * Apply the schema to the target (create/update) according to implementation policy.
     * Implementations should be idempotent.
     * @param schema schema to apply
     */
    void apply(@NonNull GeneratedSchema schema);
}

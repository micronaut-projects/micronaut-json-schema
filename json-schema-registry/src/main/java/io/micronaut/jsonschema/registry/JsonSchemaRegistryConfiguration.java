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

import io.micronaut.core.annotation.NonNull;

/**
 * Configuration for the JSON Schema Registry orchestration.
 *
 * @since 1.0.0
 */
public interface JsonSchemaRegistryConfiguration {
    String PREFIX = "json-schema.registry";

    /**
     * Whether the orchestration is enabled.
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * If true the orchestrator will only compute actions and skip applying them.
     * @return true when dry run
     */
    boolean isDryRun();

    /**
     * If true the orchestrator will fail the startup on errors (when enabled).
     * @return true when fail fast
     */
    boolean isFailFast();

    /**
     * Optional subject naming strategy id. Implementations may interpret values like "type-simple" or "type-fqn".
     * @return strategy id, never null (defaults to "type-simple")
     */
    @NonNull
    String getSubjectStrategy();

    /**
     * Optional domain prefix for Oracle 23ai JSON domains.
     * @return prefix, possibly empty
     */
    @NonNull
    String getDomainPrefix();
}

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

import io.micronaut.core.annotation.Nullable;

/**
 * Single reconciliation outcome.
 *
 * @param logicalSchema Logical schema identity
 * @param target Target name
 * @param status Outcome status
 * @param failure Whether this outcome represents a reconciliation failure
 * @param message Optional diagnostic message
 * @since 2.2.0
 */
public record JsonSchemaRegistryOutcome(
    LogicalSchema logicalSchema,
    String target,
    JsonSchemaRegistryOutcomeStatus status,
    boolean failure,
    @Nullable String message
) {

    /**
     * Create a non-failing outcome.
     *
     * @param logicalSchema Logical schema identity
     * @param target Target name
     * @param status Outcome status
     * @param message Optional message
     * @return The outcome
     */
    public static JsonSchemaRegistryOutcome ok(LogicalSchema logicalSchema,
                                               String target,
                                               JsonSchemaRegistryOutcomeStatus status,
                                               @Nullable String message) {
        return new JsonSchemaRegistryOutcome(logicalSchema, target, status, false, message);
    }

    /**
     * Create a failing outcome.
     *
     * @param logicalSchema Logical schema identity
     * @param target Target name
     * @param status Outcome status
     * @param message Optional message
     * @return The outcome
     */
    public static JsonSchemaRegistryOutcome failure(LogicalSchema logicalSchema,
                                                    String target,
                                                    JsonSchemaRegistryOutcomeStatus status,
                                                    @Nullable String message) {
        return new JsonSchemaRegistryOutcome(logicalSchema, target, status, true, message);
    }
}

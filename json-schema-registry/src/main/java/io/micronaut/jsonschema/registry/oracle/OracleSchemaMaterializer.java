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
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;

import java.sql.Connection;
import java.util.Optional;

/**
 * Oracle materializer extension point.
 *
 * @since 2.2.0
 */
public interface OracleSchemaMaterializer {

    /**
     * @return The provider class name used by configuration
     */
    default String providerClassName() {
        return getClass().getName();
    }

    /**
     * @return Human-readable description of the Oracle artifact representation supported by this materializer
     */
    default String representationDescription() {
        return "Oracle schema materializer " + providerClassName();
    }

    /**
     * Reconcile the configured Oracle artifact.
     *
     * @param connection JDBC connection
     * @param request Materialization request
     * @return Reconciliation outcome
     * @throws Exception If materialization fails
     */
    JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception;

    /**
     * Validate whether the candidate schema can be projected into this Oracle artifact.
     *
     * @param request Materialization request
     * @return Optional incompatibility outcome
     */
    default Optional<JsonSchemaRegistryOutcome> projectionCompatibility(OracleMaterializationRequest request) {
        return Optional.empty();
    }
}

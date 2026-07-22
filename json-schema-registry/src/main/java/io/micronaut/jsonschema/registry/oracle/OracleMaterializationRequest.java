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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.jsonschema.registry.JsonSchemaCandidate;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;

import java.util.Map;

/**
 * Request passed to an Oracle materializer.
 *
 * @param candidate Candidate schema
 * @param artifactName Resolved Oracle artifact name, or null when the materializer resolves it from options
 * @param owner Optional Oracle owner/schema
 * @param options Materializer options
 * @param policyMode Oracle policy mode
 * @param driftMode Oracle drift mode
 * @param dryRun Whether writes should be previewed only
 * @param operationRecorder Operation recorder
 * @since 2.2.0
 */
public record OracleMaterializationRequest(
    JsonSchemaCandidate candidate,
    @Nullable String artifactName,
    @Nullable String owner,
    Map<String, String> options,
    JsonSchemaRegistryPolicyMode policyMode,
    JsonSchemaRegistryDriftMode driftMode,
    boolean dryRun,
    OracleOperationRecorder operationRecorder
) {

    /**
     * Create an immutable materialization request.
     *
     * @param candidate Candidate schema
     * @param artifactName Resolved Oracle artifact name, or null when the materializer resolves it from options
     * @param owner Optional Oracle owner/schema
     * @param options Materializer options
     * @param policyMode Oracle policy mode
     * @param driftMode Oracle drift mode
     * @param dryRun Whether writes should be previewed only
     */
    public OracleMaterializationRequest(JsonSchemaCandidate candidate,
                                        @Nullable String artifactName,
                                        @Nullable String owner,
                                        Map<String, String> options,
                                        JsonSchemaRegistryPolicyMode policyMode,
                                        JsonSchemaRegistryDriftMode driftMode,
                                        boolean dryRun) {
        this(candidate, artifactName, owner, options, policyMode, driftMode, dryRun, OracleOperationRecorder.NOOP);
    }

    /**
     * Create an immutable materialization request.
     *
     * @param candidate Candidate schema
     * @param artifactName Resolved Oracle artifact name, or null when the materializer resolves it from options
     * @param owner Optional Oracle owner/schema
     * @param options Materializer options
     * @param policyMode Oracle policy mode
     * @param driftMode Oracle drift mode
     * @param dryRun Whether writes should be previewed only
     */
    public OracleMaterializationRequest {
        options = options == null ? Map.of() : Map.copyOf(options);
        operationRecorder = operationRecorder == null ? OracleOperationRecorder.NOOP : operationRecorder;
    }

    /**
     * Record a materializer operation.
     *
     * @param operationName Operation name
     * @param operation Operation callback
     * @param <T> Operation return type
     * @return Operation result
     * @throws Exception If the operation fails
     */
    public <T> T recordOperation(String operationName, OracleOperationRecorder.OracleOperation<T> operation) throws Exception {
        return operationRecorder.record(operationName, operation);
    }
}

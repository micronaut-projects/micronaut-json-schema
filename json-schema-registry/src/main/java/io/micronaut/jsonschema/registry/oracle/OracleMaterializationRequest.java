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

import io.micronaut.jsonschema.registry.JsonSchemaCandidate;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;

import java.util.Map;

/**
 * Request passed to an Oracle materializer.
 *
 * @param candidate Candidate schema
 * @param artifactName Resolved Oracle artifact name
 * @param owner Optional Oracle owner/schema
 * @param options Materializer options
 * @param policyMode Oracle policy mode
 * @param driftMode Oracle drift mode
 * @param dryRun Whether writes should be previewed only
 * @since 2.0.0
 */
public record OracleMaterializationRequest(
    JsonSchemaCandidate candidate,
    String artifactName,
    String owner,
    Map<String, String> options,
    JsonSchemaRegistryPolicyMode policyMode,
    JsonSchemaRegistryDriftMode driftMode,
    boolean dryRun
) {

    /**
     * Create an immutable materialization request.
     *
     * @param candidate Candidate schema
     * @param artifactName Resolved Oracle artifact name
     * @param owner Optional Oracle owner/schema
     * @param options Materializer options
     * @param policyMode Oracle policy mode
     * @param driftMode Oracle drift mode
     * @param dryRun Whether writes should be previewed only
     */
    public OracleMaterializationRequest {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}

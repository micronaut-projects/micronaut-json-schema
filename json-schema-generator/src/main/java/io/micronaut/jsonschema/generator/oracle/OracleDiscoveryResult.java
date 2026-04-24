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
package io.micronaut.jsonschema.generator.oracle;

import java.util.List;

/**
 * Result returned by an Oracle discovery provider.
 *
 * @param schemas The discovered schemas
 * @param warnings Non-fatal warnings emitted by discovery
 * @param skipped Skipped inputs recorded when {@code skipOnError=true}
 * @since 2.0.0
 */
public record OracleDiscoveryResult(
    List<OracleDiscoveredSchema> schemas,
    List<OracleDiscoveryWarning> warnings,
    List<OracleDiscoverySkipped> skipped
) {

    /**
     * Create an immutable discovery result.
     *
     * @param schemas The discovered schemas
     * @param warnings Non-fatal warnings emitted by discovery
     * @param skipped Skipped inputs recorded when {@code skipOnError=true}
     */
    public OracleDiscoveryResult {
        schemas = schemas == null ? List.of() : List.copyOf(schemas);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}

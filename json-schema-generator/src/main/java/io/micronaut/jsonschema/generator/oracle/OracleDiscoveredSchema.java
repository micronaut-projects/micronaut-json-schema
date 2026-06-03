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

/**
 * Oracle-backed JSON Schema discovered from database metadata.
 *
 * @param scope Oracle artifact scope
 * @param name Oracle artifact name
 * @param schemaJson JSON Schema text
 * @param retrievalMode Metadata retrieval mode
 * @since 2.0.0
 */
public record OracleDiscoveredSchema(
    OracleDiscoveryScope scope,
    String name,
    String schemaJson,
    String retrievalMode
) {
}

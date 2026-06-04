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
package io.micronaut.jsonschema.generator.discovery;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Execution context passed to schema discovery providers.
 *
 * @param skipOnError Whether per-object failures should be skipped
 * @param failOnMissingSource Whether unavailable configured sources should fail the build
 * @param schemaCacheDir Discovery schema cache directory
 * @param outputDir Generated source output directory
 * @param sourceMetadata Sanitized provider source metadata available to the provider
 * @param logger Logger for diagnostics
 * @param jdbcConnectionProvider JDBC connection provider for JDBC-backed discovery providers
 * @since 2.0.0
 */
public record SchemaDiscoveryContext(
    boolean skipOnError,
    boolean failOnMissingSource,
    Path schemaCacheDir,
    Path outputDir,
    Map<String, String> sourceMetadata,
    JsonSchemaRecordsLogger logger,
    JdbcConnectionProvider jdbcConnectionProvider
) {

    /**
     * Create a schema discovery context.
     *
     * @param skipOnError Whether per-object failures should be skipped
     * @param failOnMissingSource Whether unavailable configured sources should fail the build
     * @param schemaCacheDir Discovery schema cache directory
     * @param outputDir Generated source output directory
     * @param sourceMetadata Sanitized provider source metadata available to the provider
     * @param logger Logger for diagnostics
     * @param jdbcConnectionProvider JDBC connection provider for JDBC-backed discovery providers
     */
    public SchemaDiscoveryContext {
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }

    /**
     * Resolve the JDBC connection provider when the configured discovery provider needs JDBC.
     *
     * @return The JDBC connection provider when present
     */
    public Optional<JdbcConnectionProvider> findJdbcConnectionProvider() {
        return Optional.ofNullable(jdbcConnectionProvider);
    }

    /**
     * Resolve the JDBC connection provider required by JDBC-backed discovery providers.
     *
     * @return The JDBC connection provider
     */
    public JdbcConnectionProvider requireJdbcConnectionProvider() {
        return findJdbcConnectionProvider().orElseThrow(() -> new IllegalStateException("Missing JDBC connection provider for JDBC-backed schema discovery source."));
    }
}

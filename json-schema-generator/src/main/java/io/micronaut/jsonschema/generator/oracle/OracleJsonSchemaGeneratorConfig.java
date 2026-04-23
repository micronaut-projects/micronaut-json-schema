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

import io.micronaut.core.annotation.Internal;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration for Oracle schema discovery and Java record generation.
 *
 * @param jdbcUrl The JDBC URL
 * @param username The database username
 * @param password The database password
 * @param owner Optional owner for cross-schema discovery
 * @param targetPackage Target Java package
 * @param schemaCacheDir Discovery output directory
 * @param outputDir Generated source directory
 * @param includeDomains Included domain names, or {@code *}
 * @param includeViews Included duality view names, or {@code *}
 * @param sources Enabled discovery sources
 * @param skipOnError Whether to skip individual failures
 * @param failOnMissingDb Whether connection failure should fail the build
 * @since 2.0.0
 */
@Internal
public record OracleJsonSchemaGeneratorConfig(
    String jdbcUrl,
    String username,
    String password,
    String owner,
    String targetPackage,
    Path schemaCacheDir,
    Path outputDir,
    List<String> includeDomains,
    List<String> includeViews,
    List<DiscoverySource> sources,
    boolean skipOnError,
    boolean failOnMissingDb
) {

    /**
     * Discovery sources supported by the Oracle pipeline.
     */
    public enum DiscoverySource {
        ORACLE_DOMAIN("OracleDomain"),
        ORACLE_JSON_VIEW("OracleJsonView");

        private final String externalName;

        DiscoverySource(String externalName) {
            this.externalName = externalName;
        }

        public String externalName() {
            return externalName;
        }

        /**
         * Resolve the enum from the external configuration name.
         * @param value The configured source name
         * @return The enum value
         */
        public static DiscoverySource fromExternalName(String value) {
            return switch (value) {
                case "OracleDomain" -> ORACLE_DOMAIN;
                case "OracleJsonView" -> ORACLE_JSON_VIEW;
                default -> throw new IllegalArgumentException("Unsupported source: " + value);
            };
        }
    }

    /**
     * Discovery object scope used in diagnostics.
     */
    public enum Scope {
        DOMAIN,
        DUALITY_VIEW
    }

    /**
     * Pipeline step used in diagnostics.
     */
    public enum Step {
        DISCOVERY,
        SCHEMA_RETRIEVAL,
        GENERATION
    }

    public OracleJsonSchemaGeneratorConfig {
        includeDomains = includeDomains == null ? List.of() : List.copyOf(includeDomains);
        includeViews = includeViews == null ? List.of() : List.copyOf(includeViews);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /**
     * Resolve the effective discovery sources.
     * @return The enabled sources
     */
    public Set<DiscoverySource> resolveSources() {
        if (!sources.isEmpty()) {
            return new LinkedHashSet<>(sources);
        }
        Set<DiscoverySource> resolved = new LinkedHashSet<>();
        if (!includeDomains.isEmpty()) {
            resolved.add(DiscoverySource.ORACLE_DOMAIN);
        }
        if (!includeViews.isEmpty()) {
            resolved.add(DiscoverySource.ORACLE_JSON_VIEW);
        }
        if (resolved.isEmpty()) {
            throw new IllegalStateException("At least one source or include list must be configured.");
        }
        return resolved;
    }

    /**
     * Whether all visible objects should be discovered for the given source.
     * @param source The source
     * @return True if discover-all should be used
     */
    public boolean discoverAll(DiscoverySource source) {
        List<String> includes = source == DiscoverySource.ORACLE_DOMAIN ? includeDomains : includeViews;
        return includes.isEmpty() || includes.contains("*");
    }

    /**
     * Resolve the uppercase include filter for the given source.
     * @param source The source
     * @return The filter set
     */
    public Set<String> includeFilter(DiscoverySource source) {
        List<String> includes = source == DiscoverySource.ORACLE_DOMAIN ? includeDomains : includeViews;
        if (discoverAll(source)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String include : includes) {
            values.add(include.toUpperCase(Locale.ENGLISH));
        }
        return values;
    }
}

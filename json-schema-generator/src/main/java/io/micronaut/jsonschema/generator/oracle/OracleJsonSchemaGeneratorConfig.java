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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration for Oracle schema discovery and Java record generation.
 *
 * @param jdbcUrl The JDBC URL
 * @param username The database username
 * @param password The database password
 * @param targetPackage Target Java package
 * @param schemaCacheDir Discovery output directory
 * @param outputDir Generated source directory
 * @param sources Configured discovery sources
 * @param skipOnError Whether to skip individual failures
 * @param failOnMissingDb Whether connection failure should fail the build
 * @since 2.0.0
 */
@Internal
public record OracleJsonSchemaGeneratorConfig(
    String jdbcUrl,
    String username,
    String password,
    String targetPackage,
    Path schemaCacheDir,
    Path outputDir,
    List<OracleSourceSpec> sources,
    boolean skipOnError,
    boolean failOnMissingDb
) {

    /**
     * Create a normalized generator configuration.
     *
     * @param jdbcUrl The JDBC URL
     * @param username The database username
     * @param password The database password
     * @param targetPackage Target Java package
     * @param schemaCacheDir Discovery output directory
     * @param outputDir Generated source directory
     * @param sources Configured discovery sources
     * @param skipOnError Whether to skip individual failures
     * @param failOnMissingDb Whether connection failure should fail the build
     */
    public OracleJsonSchemaGeneratorConfig {
        sources = normalizeSources(sources);
    }

    /**
     * Resolve the configured sources.
     *
     * @return The normalized configured sources
     */
    public List<OracleSourceSpec> resolveSources() {
        if (sources.isEmpty()) {
            throw new IllegalStateException("At least one Oracle JSON Schema source must be configured.");
        }
        return sources;
    }

    private static List<OracleSourceSpec> normalizeSources(List<OracleSourceSpec> configuredSources) {
        if (configuredSources == null || configuredSources.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        List<OracleSourceSpec> normalized = new ArrayList<>(configuredSources.size());
        for (OracleSourceSpec source : configuredSources) {
            if (source == null) {
                continue;
            }
            String providerClassName = requireValue("providerClassName", source.providerClassName());
            String normalizedName = normalizeName(source.name(), providerClassName, occurrences);
            normalized.add(new OracleSourceSpec(
                normalizedName,
                providerClassName,
                blankToNull(source.owner()),
                source.options()
            ));
        }
        return List.copyOf(normalized);
    }

    private static String normalizeName(String name, String providerClassName, Map<String, Integer> occurrences) {
        String baseName = blankToNull(name);
        if (baseName == null) {
            baseName = providerClassName.substring(providerClassName.lastIndexOf('.') + 1);
        }
        String normalizedBase = baseName.trim();
        int count = occurrences.merge(normalizedBase.toLowerCase(Locale.ENGLISH), 1, Integer::sum);
        return count == 1 ? normalizedBase : normalizedBase + "-" + count;
    }

    private static String requireValue(String name, String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Missing Oracle JSON Schema source " + name + ".");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

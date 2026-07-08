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
package io.micronaut.jsonschema.generator.records;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration for schema discovery and source generation.
 *
 * @param jdbcUrl The JDBC URL
 * @param username The database username
 * @param password The database password
 * @param targetPackage Target package
 * @param languageLevel Java language level used for Java generation
 * @param schemaCacheDir Discovery output directory
 * @param outputDir Generated source directory
 * @param sources Configured discovery sources
 * @param skipOnError Whether to skip individual failures
 * @param failOnMissingSource Whether unavailable configured sources should fail the build
 * @param language Generated source language
 * @since 2.1.0
 */
@Internal
public record JsonSchemaRecordsGeneratorConfig(
    String jdbcUrl,
    String username,
    String password,
    String targetPackage,
    int languageLevel,
    Path schemaCacheDir,
    Path outputDir,
    List<SourceSpec> sources,
    boolean skipOnError,
    boolean failOnMissingSource,
    String language
) {

    /**
     * Create a Java generation configuration.
     *
     * @param jdbcUrl The JDBC URL
     * @param username The database username
     * @param password The database password
     * @param targetPackage Target package
     * @param languageLevel Java language level used for Java generation
     * @param schemaCacheDir Discovery output directory
     * @param outputDir Generated source directory
     * @param sources Configured discovery sources
     * @param skipOnError Whether to skip individual failures
     * @param failOnMissingSource Whether unavailable configured sources should fail the build
     */
    public JsonSchemaRecordsGeneratorConfig(String jdbcUrl,
                                            String username,
                                            String password,
                                            String targetPackage,
                                            int languageLevel,
                                            Path schemaCacheDir,
                                            Path outputDir,
                                            List<SourceSpec> sources,
                                            boolean skipOnError,
                                            boolean failOnMissingSource) {
        this(jdbcUrl, username, password, targetPackage, languageLevel, schemaCacheDir, outputDir, sources,
            skipOnError, failOnMissingSource, "JAVA");
    }

    /**
     * Create a normalized generator configuration.
     *
     * @param jdbcUrl The JDBC URL
     * @param username The database username
     * @param password The database password
     * @param targetPackage Target package
     * @param languageLevel Java language level used for Java generation
     * @param schemaCacheDir Discovery output directory
     * @param outputDir Generated source directory
     * @param sources Configured discovery sources
     * @param skipOnError Whether to skip individual failures
     * @param failOnMissingSource Whether unavailable configured sources should fail the build
     * @param language Generated source language
     */
    public JsonSchemaRecordsGeneratorConfig {
        if (languageLevel <= 0) {
            throw new IllegalArgumentException("jsonSchemaRecords languageLevel must be a positive integer.");
        }
        language = normalizeLanguage(language);
        sources = normalizeSources(sources);
    }

    /**
     * @return The SourceGen language used for generation
     */
    public VisitorContext.Language sourceLanguage() {
        return VisitorContext.Language.valueOf(language);
    }

    /**
     * @return Generated source file extension without a leading dot
     */
    public String sourceFileExtension() {
        return "KOTLIN".equals(language) ? "kt" : "java";
    }

    /**
     * Resolve the configured sources.
     *
     * @return The normalized configured sources
     */
    public List<SourceSpec> resolveSources() {
        if (sources.isEmpty()) {
            throw new IllegalStateException("At least one jsonSchemaRecords source must be configured.");
        }
        return sources;
    }

    private static List<SourceSpec> normalizeSources(List<SourceSpec> configuredSources) {
        if (configuredSources == null || configuredSources.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        List<SourceSpec> normalized = new ArrayList<>(configuredSources.size());
        for (SourceSpec source : configuredSources) {
            if (source == null) {
                continue;
            }
            String provider = requireValue("provider", source.provider());
            String normalizedName = normalizeName(source.name(), provider, occurrences);
            normalized.add(new SourceSpec(
                normalizedName,
                provider,
                source.options()
            ));
        }
        return List.copyOf(normalized);
    }

    private static String normalizeName(String name, String provider, Map<String, Integer> occurrences) {
        String baseName = blankToNull(name);
        if (baseName == null) {
            baseName = provider;
        }
        String normalizedBase = baseName.trim();
        int count = occurrences.merge(normalizedBase.toLowerCase(Locale.ENGLISH), 1, Integer::sum);
        return count == 1 ? normalizedBase : normalizedBase + "-" + count;
    }

    private static String requireValue(String name, String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Missing jsonSchemaRecords source " + name + ".");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeLanguage(String configuredLanguage) {
        String normalized = configuredLanguage == null ? "JAVA" : configuredLanguage.trim().toUpperCase(Locale.ENGLISH);
        if (!"JAVA".equals(normalized) && !"KOTLIN".equals(normalized)) {
            throw new IllegalArgumentException("jsonSchemaRecords language must be JAVA or KOTLIN.");
        }
        return normalized;
    }
}

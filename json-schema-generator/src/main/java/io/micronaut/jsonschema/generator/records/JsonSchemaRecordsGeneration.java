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

import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import io.micronaut.sourcegen.annotations.PluginTask;
import io.micronaut.sourcegen.annotations.PluginTaskExecutable;
import io.micronaut.sourcegen.annotations.PluginTaskParameter;
import io.micronaut.sourcegen.annotations.PluginTaskParameter.OutputType;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared build-plugin task definition for JSON Schema discovery and Java record generation.
 *
 * @param jdbcUrl JDBC URL
 * @param username Database username
 * @param password Database password
 * @param tnsAdmin Oracle TNS admin directory
 * @param walletLocation Oracle wallet location
 * @param jdbcProperties Additional JDBC connection properties
 * @param targetPackage Target package for generated Java sources
 * @param languageLevel Java language level used for generated sources
 * @param schemaCacheDir Directory where discovered schemas are cached
 * @param outputDir Directory where Java sources are generated
 * @param sources Configured schema discovery sources
 * @param skipOnError Whether per-object discovery and generation failures should be skipped
 * @param failOnMissingSource Whether unavailable configured sources should fail the build
 * @since 2.0.0
 */
@PluginTask
public record JsonSchemaRecordsGeneration(
    @PluginTaskParameter(internal = true)
    String jdbcUrl,

    @PluginTaskParameter(internal = true)
    String username,

    @PluginTaskParameter(internal = true)
    String password,

    @PluginTaskParameter(internal = true)
    String tnsAdmin,

    @PluginTaskParameter(internal = true)
    String walletLocation,

    @PluginTaskParameter(internal = true)
    Map<String, String> jdbcProperties,

    @PluginTaskParameter(required = true, internal = true)
    String targetPackage,

    @PluginTaskParameter(defaultValue = "21", internal = true)
    Integer languageLevel,

    @PluginTaskParameter(required = true, internal = true, directory = true, output = OutputType.CUSTOM)
    File schemaCacheDir,

    @PluginTaskParameter(required = true, internal = true, directory = true, output = OutputType.JAVA_SOURCES)
    File outputDir,

    @PluginTaskParameter(required = true, internal = true)
    List<Map<String, Object>> sources,

    @PluginTaskParameter(defaultValue = "false", internal = true)
    Boolean skipOnError,

    @PluginTaskParameter(defaultValue = "true", internal = true)
    Boolean failOnMissingSource
) {

    /**
     * Create a normalized generation task definition.
     *
     * @param jdbcUrl JDBC URL
     * @param username Database username
     * @param password Database password
     * @param tnsAdmin Oracle TNS admin directory
     * @param walletLocation Oracle wallet location
     * @param jdbcProperties Additional JDBC connection properties
     * @param targetPackage Target package for generated Java sources
     * @param languageLevel Java language level used for generated sources
     * @param schemaCacheDir Directory where discovered schemas are cached
     * @param outputDir Directory where Java sources are generated
     * @param sources Configured schema discovery sources
     * @param skipOnError Whether per-object discovery and generation failures should be skipped
     * @param failOnMissingSource Whether unavailable configured sources should fail the build
     */
    public JsonSchemaRecordsGeneration {
        languageLevel = languageLevel == null ? 21 : languageLevel;
        jdbcProperties = jdbcProperties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(jdbcProperties));
        sources = sources == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(sources));
        skipOnError = skipOnError != null && skipOnError;
        failOnMissingSource = failOnMissingSource == null || failOnMissingSource;
    }

    @PluginTaskExecutable
    public void generate() {
        try {
            generate(systemLogger(), Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            throw new IllegalStateException("jsonSchemaRecords generation failed", e);
        }
    }

    /**
     * Execute generation with build-tool-specific services.
     *
     * @param logger The logger to use
     * @param providerClassLoader The classloader used to resolve discovery providers
     * @throws Exception If generation fails
     */
    public void generate(JsonSchemaRecordsLogger logger, ClassLoader providerClassLoader) throws Exception {
        new JsonSchemaRecordsPipeline(logger, providerClassLoader).execute(toGeneratorConfig());
    }

    /**
     * Convert this task definition to the generator configuration.
     *
     * @return The generator configuration
     */
    public JsonSchemaRecordsGeneratorConfig toGeneratorConfig() {
        return new JsonSchemaRecordsGeneratorConfig(
            jdbcUrl,
            username,
            password,
            tnsAdmin,
            walletLocation,
            jdbcProperties,
            targetPackage,
            languageLevel,
            schemaCacheDir.toPath(),
            outputDir.toPath(),
            toSourceSpecs(sources),
            skipOnError,
            failOnMissingSource
        );
    }

    private static List<SourceSpec> toSourceSpecs(List<Map<String, Object>> configuredSources) {
        return configuredSources.stream()
            .map(JsonSchemaRecordsGeneration::toSourceSpec)
            .toList();
    }

    private static SourceSpec toSourceSpec(Map<String, Object> sourceMap) {
        Map<String, Object> safeMap = sourceMap == null ? Map.of() : sourceMap;
        Map<String, Object> options = toOptionMap(safeMap.get("options"));
        options.putAll(toOptionMap(safeMap.get("optionValues")));
        return new SourceSpec(
            toStringValue(safeMap.get("name")),
            requiredStringValue("providerClassName", safeMap.get("providerClassName")),
            options
        );
    }

    private static Map<String, Object> toOptionMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map<?, ?> options)) {
            throw new IllegalArgumentException("Schema source options must be configured as a map.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : options.entrySet()) {
            result.put(String.valueOf(entry.getKey()), toOptionValue(entry.getValue()));
        }
        return result;
    }

    private static Object toOptionValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object element : iterable) {
                values.add(toOptionValue(element));
            }
            return values;
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(toOptionValue(Array.get(value, i)));
            }
            return values;
        }
        return value;
    }

    private static String requiredStringValue(String name, Object value) {
        String stringValue = toStringValue(value);
        if (stringValue == null || stringValue.isBlank()) {
            throw new IllegalArgumentException("Missing required schema source field: " + name);
        }
        return stringValue;
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static JsonSchemaRecordsLogger systemLogger() {
        return new JsonSchemaRecordsLogger() {
            @Override
            public void info(String message) {
                System.out.println(message);
            }

            @Override
            public void warn(String message) {
                System.err.println(message);
            }
        };
    }

}

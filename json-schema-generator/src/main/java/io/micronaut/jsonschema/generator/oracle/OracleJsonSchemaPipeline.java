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
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.RecordAdoptionStrategy;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Oracle discovery and Java record generation pipeline.
 *
 * @since 2.0.0
 */
@Internal
public final class OracleJsonSchemaPipeline {

    private static final String GENERATOR_NAME = "oracle-json-schema-record-generator";
    private final ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper();
    private final OracleJsonSchemaLogger logger;
    private final ClassLoader providerClassLoader;

    /**
     * Create a new pipeline.
     *
     * @param logger The logger to use
     */
    public OracleJsonSchemaPipeline(OracleJsonSchemaLogger logger) {
        this(logger, OracleJsonSchemaPipeline.class.getClassLoader());
    }

    /**
     * Create a new pipeline.
     *
     * @param logger The logger to use
     * @param providerClassLoader The classloader used to resolve custom discovery providers
     */
    public OracleJsonSchemaPipeline(OracleJsonSchemaLogger logger, ClassLoader providerClassLoader) {
        this.logger = logger;
        this.providerClassLoader = providerClassLoader;
    }

    /**
     * Execute discovery and source generation.
     *
     * @param config The generator configuration
     * @return The execution result
     * @throws IOException If file I/O fails
     * @throws SQLException If database access fails
     */
    public Result execute(OracleJsonSchemaGeneratorConfig config) throws IOException, SQLException {
        List<OracleSourceSpec> configuredSources = config.resolveSources();
        Files.createDirectories(config.schemaCacheDir());
        Files.createDirectories(config.outputDir());

        List<OracleJsonSchemaManifest.Warning> warnings = new ArrayList<>();
        List<OracleJsonSchemaManifest.Skipped> skipped = new ArrayList<>();
        List<DiscoveredSchemaEntry> discoveredSchemas = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            logger.info("[oracle-jsonschema] INFO jdbcUrl=" + sanitizeJdbcUrl(config.jdbcUrl()) + " sources=" + configuredSources.size());
            for (OracleSourceSpec source : configuredSources) {
                OracleSchemaDiscoveryProvider provider = OracleSchemaDiscoveryProviders.resolve(source.providerClassName(), providerClassLoader);
                OracleDiscoveryResult result = provider.discover(connection, source, config.skipOnError(), logger);
                for (OracleDiscoveryWarning warning : result.warnings()) {
                    warnings.add(new OracleJsonSchemaManifest.Warning(
                        source.name(),
                        warning.scope(),
                        warning.name(),
                        warning.step(),
                        warning.code(),
                        warning.message()
                    ));
                    logger.warn(formatWarning(source.name(), warning.scope(), warning.name(), warning.step(), warning.code(), warning.message()));
                }
                for (OracleDiscoverySkipped skippedEntry : result.skipped()) {
                    skipped.add(new OracleJsonSchemaManifest.Skipped(
                        source.name(),
                        skippedEntry.scope(),
                        skippedEntry.name(),
                        skippedEntry.step(),
                        skippedEntry.code(),
                        skippedEntry.reason(),
                        skippedEntry.retrievalMode()
                    ));
                    logger.warn(formatWarning(source.name(), skippedEntry.scope(), skippedEntry.name(), skippedEntry.step(), skippedEntry.code(), skippedEntry.reason()));
                }
                for (OracleDiscoveredSchema schema : result.schemas()) {
                    discoveredSchemas.add(new DiscoveredSchemaEntry(source, schema));
                }
            }
        } catch (SQLException e) {
            if (config.failOnMissingDb()) {
                throw e;
            }
            logger.warn("[oracle-jsonschema] WARN scope=DOMAIN name=* step=DISCOVERY code=DB_UNAVAILABLE msg=\"" + escape(e.getMessage()) + "\"");
            return new Result(null, 0);
        } catch (Exception e) {
            throw new IOException("Oracle JSON Schema discovery failed", e);
        }

        List<OracleJsonSchemaManifest.SchemaFile> schemaEntries = new ArrayList<>();
        List<String> emittedFiles = new ArrayList<>();
        Map<String, DiscoveredSchemaEntry> byRelativeFile = new LinkedHashMap<>();

        discoveredSchemas.sort(Comparator
            .comparing((DiscoveredSchemaEntry entry) -> entry.source().name())
            .thenComparing(entry -> entry.schema().name()));
        for (DiscoveredSchemaEntry discovered : discoveredSchemas) {
            Path relativePath = Path.of(
                "sources",
                sanitizeSegment(discovered.source().name()),
                sanitizeFileName(discovered.schema().name(), discovered.source().owner())
            );
            Path outputPath = config.schemaCacheDir().resolve(relativePath);
            Files.createDirectories(outputPath.getParent());
            writeCanonicalJson(outputPath, discovered.schema().schemaJson());
            String relativeFile = relativePath.toString().replace('\\', '/');
            emittedFiles.add(relativeFile);
            byRelativeFile.put(relativeFile, discovered);
            schemaEntries.add(new OracleJsonSchemaManifest.SchemaFile(
                discovered.source().name(),
                discovered.source().providerClassName(),
                discovered.schema().scope(),
                discovered.schema().name(),
                relativeFile,
                discovered.schema().retrievalMode()
            ));
        }

        int generatedTypes = generateSources(config, byRelativeFile, warnings, skipped);
        Path manifestPath = writeManifest(config, warnings, skipped, schemaEntries, emittedFiles);
        logger.info("[oracle-jsonschema] INFO generatedTypes=" + generatedTypes + " outputDir=" + config.outputDir());
        return new Result(manifestPath, generatedTypes);
    }

    private int generateSources(OracleJsonSchemaGeneratorConfig config,
                                Map<String, DiscoveredSchemaEntry> discoveredSchemas,
                                List<OracleJsonSchemaManifest.Warning> warnings,
                                List<OracleJsonSchemaManifest.Skipped> skipped) throws IOException {
        int generated = 0;
        for (Map.Entry<String, DiscoveredSchemaEntry> entry : discoveredSchemas.entrySet()) {
            DiscoveredSchemaEntry discovered = entry.getValue();
            OracleDiscoveredSchema schema = discovered.schema();
            SourceGenerator generator = new SourceGenerator("java");
            try {
                if (generator.generate(new SourceGeneratorConfig(
                    null,
                    null,
                    config.schemaCacheDir().resolve(entry.getKey()).toFile(),
                    null,
                    config.outputDir(),
                    config.targetPackage(),
                    toTypeName(schema.name()),
                    new SourceGeneratorConfig.JavadocConfig(),
                    RecordAdoptionStrategy.PREFER_RECORD,
                    true,
                    true,
                    true,
                    true
                )) != null) {
                    generated++;
                }
                for (GeneratorContext.Warning warning : generator.getWarnings()) {
                    warnings.add(new OracleJsonSchemaManifest.Warning(discovered.source().name(), schema.scope(), schema.name(), OracleDiscoveryStep.GENERATION, warning.code(), warning.message()));
                    logger.warn(formatWarning(discovered.source().name(), schema.scope(), schema.name(), OracleDiscoveryStep.GENERATION, warning.code(), warning.message()));
                }
            } catch (Exception e) {
                if (config.skipOnError()) {
                    skipped.add(new OracleJsonSchemaManifest.Skipped(
                        discovered.source().name(),
                        schema.scope(),
                        schema.name(),
                        OracleDiscoveryStep.GENERATION,
                        "GENERATION_FAILED",
                        e.getMessage(),
                        schema.retrievalMode()
                    ));
                    logger.warn(formatWarning(discovered.source().name(), schema.scope(), schema.name(), OracleDiscoveryStep.GENERATION, "GENERATION_FAILED", e.getMessage()));
                } else {
                    throw new IOException(e.getMessage(), e);
                }
            }
        }
        return generated;
    }

    private Path writeManifest(OracleJsonSchemaGeneratorConfig config,
                               List<OracleJsonSchemaManifest.Warning> warnings,
                               List<OracleJsonSchemaManifest.Skipped> skipped,
                               List<OracleJsonSchemaManifest.SchemaFile> schemaEntries,
                               List<String> emittedFiles) throws IOException {
        OracleJsonSchemaManifest manifest = new OracleJsonSchemaManifest(
            new OracleJsonSchemaManifest.Generator(GENERATOR_NAME, Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("dev")),
            Instant.now().toString(),
            new OracleJsonSchemaManifest.Connection(sanitizeJdbcUrl(config.jdbcUrl())),
            new OracleJsonSchemaManifest.Parameters(
                config.targetPackage(),
                config.schemaCacheDir().toString(),
                config.outputDir().toString(),
                config.sources().stream()
                    .map(source -> new OracleJsonSchemaManifest.ConfiguredSource(
                        source.name(),
                        source.providerClassName(),
                        blankToNull(source.owner()),
                        source.options()
                    ))
                    .toList(),
                config.skipOnError(),
                config.failOnMissingDb()
            ),
            new OracleJsonSchemaManifest.Discovery(schemaEntries),
            warnings,
            skipped,
            emittedFiles
        );
        Path manifestPath = config.schemaCacheDir().resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        return manifestPath;
    }

    private void writeCanonicalJson(Path outputPath, String jsonSchema) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(jsonSchema);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), jsonNode);
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        String sanitized = jdbcUrl.replaceAll("\\?.*$", "");
        sanitized = sanitized.replaceAll("(jdbc:oracle:thin:)([^@/]+/[^@]+@)", "$1");
        return sanitized;
    }

    private String sanitizeSegment(String value) {
        return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String sanitizeFileName(String name, String owner) {
        String sanitized = name.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9_]", "_");
        if (owner != null && !owner.isBlank()) {
            return owner.toUpperCase(Locale.ENGLISH) + "_" + sanitized + ".schema.json";
        }
        return sanitized + ".schema.json";
    }

    private String toTypeName(String value) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;
        for (char character : value.toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                builder.append(capitalizeNext ? Character.toUpperCase(character) : Character.toLowerCase(character));
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }
        return builder.isEmpty() ? "GeneratedSchema" : builder.toString();
    }

    private String formatWarning(String sourceName, OracleDiscoveryScope scope, String name, OracleDiscoveryStep step, String code, String message) {
        return "[oracle-jsonschema] WARN source=" + sourceName + " scope=" + scope + " name=" + name + " step=" + step + " code=" + code + " msg=\"" + escape(message) + "\"";
    }

    private String escape(String message) {
        return message == null ? "" : message.replace("\"", "'");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Execution result.
     *
     * @param manifestPath Written manifest path, when one was emitted
     * @param generatedTypes Number of generated top-level types
     */
    public record Result(Path manifestPath, int generatedTypes) {
    }

    private record DiscoveredSchemaEntry(OracleSourceSpec source, OracleDiscoveredSchema schema) {
    }
}

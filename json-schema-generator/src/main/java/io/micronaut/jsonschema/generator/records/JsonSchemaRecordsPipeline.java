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
import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema;
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult;
import io.micronaut.jsonschema.generator.discovery.DiscoverySkipped;
import io.micronaut.jsonschema.generator.discovery.DiscoveryStep;
import io.micronaut.jsonschema.generator.discovery.DiscoveryWarning;
import io.micronaut.jsonschema.generator.discovery.JdbcConnectionProvider;
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProviders;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import io.micronaut.jsonschema.generator.discovery.SourceUnavailableException;
import io.micronaut.jsonschema.generator.SchemaReferenceCompositionSupport;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.loaders.FileLoader;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.RecordAdoptionStrategy;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Schema discovery and Java record generation pipeline.
 *
 * @since 2.1.0
 */
@Internal
public final class JsonSchemaRecordsPipeline {

    private static final String GENERATOR_NAME = "json-schema-record-generator";
    private static final String DRAFT_2020_12_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    private static final String NAME_COLLISION = "NAME_COLLISION";
    private static final int MAX_CACHE_NAME_LENGTH = 128;
    private final ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper();
    private final JsonSchemaRecordsLogger logger;
    private final ClassLoader providerClassLoader;

    /**
     * Create a new pipeline.
     *
     * @param logger The logger to use
     */
    public JsonSchemaRecordsPipeline(JsonSchemaRecordsLogger logger) {
        this(logger, JsonSchemaRecordsPipeline.class.getClassLoader());
    }

    /**
     * Create a new pipeline.
     *
     * @param logger The logger to use
     * @param providerClassLoader The classloader used to resolve custom discovery providers
     */
    public JsonSchemaRecordsPipeline(JsonSchemaRecordsLogger logger, ClassLoader providerClassLoader) {
        this.logger = logger;
        this.providerClassLoader = providerClassLoader;
    }

    /**
     * Execute discovery and source generation.
     *
     * @param config The generator configuration
     * @return The execution result
     * @throws IOException If file I/O fails
     * @throws SQLException If a required JDBC-backed source cannot be reached
     */
    public Result execute(JsonSchemaRecordsGeneratorConfig config) throws IOException, SQLException {
        List<SourceSpec> configuredSources = config.resolveSources();
        Files.createDirectories(config.schemaCacheDir());
        Files.createDirectories(config.outputDir());

        List<JsonSchemaRecordsManifest.Warning> warnings = new ArrayList<>();
        List<JsonSchemaRecordsManifest.Skipped> skipped = new ArrayList<>();
        List<DiscoveredSchemaEntry> discoveredSchemas = new ArrayList<>();
        Map<String, Map<String, String>> sourceMetadata = new LinkedHashMap<>();

        try (LazyJdbcConnectionProvider jdbcConnectionProvider = new LazyJdbcConnectionProvider(config)) {
            logger.info("[jsonschema-records] INFO sources=" + configuredSources.size());
            logEffectiveParameters(config);
            for (SourceSpec source : configuredSources) {
                SchemaDiscoveryProvider provider = SchemaDiscoveryProviders.resolve(source.provider(), providerClassLoader);
                Map<String, String> contextSourceMetadata = sourceMetadata(provider, config);
                sourceMetadata.put(source.name(), contextSourceMetadata);
                SchemaDiscoveryContext context = new SchemaDiscoveryContext(
                    config.skipOnError(),
                    config.failOnMissingSource(),
                    config.schemaCacheDir(),
                    config.outputDir(),
                    contextSourceMetadata,
                    logger,
                    jdbcConnectionProvider
                );
                DiscoveryResult result;
                try {
                    result = provider.discover(context, source);
                    sourceMetadata.put(source.name(), mergeSourceMetadata(contextSourceMetadata, result.sourceMetadata()));
                } catch (SourceUnavailableException e) {
                    if (config.failOnMissingSource()) {
                        throw new IOException(e.getMessage(), e);
                    }
                    warnings.add(new JsonSchemaRecordsManifest.Warning(
                        source.name(),
                        e.scope(),
                        e.name(),
                        e.step(),
                        e.code(),
                        e.getMessage()
                    ));
                    logger.warn(formatWarning(source.name(), e.scope(), e.name(), e.step(), e.code(), e.getMessage()));
                    continue;
                } catch (SQLException e) {
                    if (config.failOnMissingSource()) {
                        throw e;
                    }
                    String scope = provider.sourceScope();
                    warnings.add(new JsonSchemaRecordsManifest.Warning(
                        source.name(),
                        scope,
                        null,
                        DiscoveryStep.DISCOVERY,
                        "SOURCE_UNAVAILABLE",
                        e.getMessage()
                    ));
                    logger.warn(formatWarning(source.name(), scope, null, DiscoveryStep.DISCOVERY, "SOURCE_UNAVAILABLE", e.getMessage()));
                    continue;
                }
                for (DiscoveryWarning warning : result.warnings()) {
                    warnings.add(new JsonSchemaRecordsManifest.Warning(
                        source.name(),
                        warning.scope(),
                        warning.name(),
                        warning.step(),
                        warning.code(),
                        warning.message()
                    ));
                    logger.warn(formatWarning(source.name(), warning.scope(), warning.name(), warning.step(), warning.code(), warning.message()));
                }
                for (DiscoverySkipped skippedEntry : result.skipped()) {
                    warnings.add(new JsonSchemaRecordsManifest.Warning(
                        source.name(),
                        skippedEntry.scope(),
                        skippedEntry.name(),
                        skippedEntry.step(),
                        skippedEntry.code(),
                        skippedEntry.reason()
                    ));
                    skipped.add(new JsonSchemaRecordsManifest.Skipped(
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
                for (DiscoveredSchema schema : result.schemas()) {
                    discoveredSchemas.add(new DiscoveredSchemaEntry(source, schema));
                }
            }
        } catch (SQLException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("jsonSchemaRecords discovery failed", e);
        }
        logger.info("[jsonschema-records] INFO discoveredSchemas=" + discoveredSchemas.size());

        List<JsonSchemaRecordsManifest.SchemaFile> schemaEntries = new ArrayList<>();
        List<String> emittedFiles = new ArrayList<>();
        Map<String, DiscoveredSchemaEntry> byRelativeFile = new LinkedHashMap<>();

        discoveredSchemas.sort(Comparator
            .comparing((DiscoveredSchemaEntry entry) -> entry.source().name())
            .thenComparing(entry -> entry.schema().name()));
        for (DiscoveredSchemaEntry discovered : discoveredSchemas) {
            Path sourceDirectory = Path.of(
                "sources",
                sanitizeSegment(discovered.source().name())
            );
            Path relativePath = uniqueSchemaPath(sourceDirectory, sanitizeFileName(discovered.schema().name(), discovered.source().option("owner")), byRelativeFile.keySet());
            Path outputPath = config.schemaCacheDir().resolve(relativePath);
            Files.createDirectories(outputPath.getParent());
            writeCanonicalJson(outputPath, discovered.schema().schemaJson());
            String relativeFile = relativePath.toString().replace('\\', '/');
            emittedFiles.add(relativeFile);
            byRelativeFile.put(relativeFile, discovered);
            schemaEntries.add(new JsonSchemaRecordsManifest.SchemaFile(
                discovered.source().name(),
                discovered.source().provider(),
                discovered.schema().scope(),
                discovered.schema().name(),
                relativeFile,
                discovered.schema().retrievalMode()
            ));
        }

        List<String> generatedJavaFiles = new ArrayList<>();
        int generatedTypes = generateSources(config, byRelativeFile, warnings, skipped, generatedJavaFiles);
        Path manifestPath = writeManifest(config, sourceMetadata, warnings, skipped, schemaEntries, emittedFiles, generatedJavaFiles);
        logger.info("[jsonschema-records] INFO generatedTypes=" + generatedTypes + " outputDir=" + config.outputDir());
        return new Result(manifestPath, generatedTypes);
    }

    private int generateSources(JsonSchemaRecordsGeneratorConfig config,
                                Map<String, DiscoveredSchemaEntry> discoveredSchemas,
                                List<JsonSchemaRecordsManifest.Warning> warnings,
                                List<JsonSchemaRecordsManifest.Skipped> skipped,
                                List<String> generatedJavaFiles) throws IOException {
        int generated = 0;
        List<GenerationPlan> generationPlans = discoveredSchemas.entrySet().stream()
            .map(entry -> planGeneration(config, entry.getKey(), entry.getValue()))
            .toList();
        Set<GenerationPlan> collisionSkips = resolveNameCollisions(config, generationPlans, warnings, skipped);
        for (GenerationPlan plan : generationPlans) {
            if (collisionSkips.contains(plan)) {
                continue;
            }
            DiscoveredSchemaEntry discovered = plan.discovered();
            DiscoveredSchema schema = discovered.schema();
            GeneratorContext generatorContext = new GeneratorContext();
            generatorContext.enableJsonSchemaRecordsProfile();
            SourceGenerator generator = new SourceGenerator(VisitorContext.Language.JAVA, generatorContext);
            try {
                Schema rootSchema = loadSchema(config, plan);
                warnIfNonDefaultDialect(rootSchema, discovered, warnings);
                SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(rootSchema);
                prepareRootAnyOf(rootSchema);
                validateRootSchema(rootSchema, plan);
                Set<String> beforeGeneration = generatedJavaFiles(config.outputDir());
                SourceGeneratorConfig sourceGeneratorConfig = new SourceGeneratorConfig(
                    null,
                    null,
                    config.schemaCacheDir().resolve(plan.schemaFile()).toFile(),
                    null,
                    config.outputDir(),
                    config.targetPackage(),
                    plan.topLevelTypeName(),
                    new SourceGeneratorConfig.JavadocConfig(),
                    recordAdoptionStrategy(config.languageLevel())
                );
                File generatedFile = generator.generate(sourceGeneratorConfig, rootSchema);
                if (generatedFile != null) {
                    generated++;
                }
                addGeneratedJavaFiles(config.outputDir(), beforeGeneration, generatedJavaFiles, plan.outputFile());
                for (GeneratorContext.Warning warning : generator.getWarnings()) {
                    warnings.add(new JsonSchemaRecordsManifest.Warning(discovered.source().name(), schema.scope(), schema.name(), DiscoveryStep.GENERATION, warning.code(), warning.message()));
                    logger.warn(formatWarning(discovered.source().name(), schema.scope(), schema.name(), DiscoveryStep.GENERATION, warning.code(), warning.message()));
                }
            } catch (Exception e) {
                if (config.skipOnError()) {
                    String code = diagnosticCode(e);
                    warnings.add(new JsonSchemaRecordsManifest.Warning(
                        discovered.source().name(),
                        schema.scope(),
                        schema.name(),
                        DiscoveryStep.GENERATION,
                        code,
                        e.getMessage()
                    ));
                    skipped.add(new JsonSchemaRecordsManifest.Skipped(
                        discovered.source().name(),
                        schema.scope(),
                        schema.name(),
                        DiscoveryStep.GENERATION,
                        code,
                        e.getMessage(),
                        schema.retrievalMode()
                    ));
                    logger.warn(formatWarning(discovered.source().name(), schema.scope(), schema.name(), DiscoveryStep.GENERATION, code, e.getMessage()));
                } else {
                    String code = diagnosticCode(e);
                    String message = e.getMessage();
                    if (!"GENERATION_FAILED".equals(code) && (message == null || !message.contains(code))) {
                        message = code + ": " + message;
                    }
                    throw new IOException(message, e);
                }
            }
        }
        return generated;
    }

    private Schema loadSchema(JsonSchemaRecordsGeneratorConfig config, GenerationPlan plan) throws IOException {
        Schema schema = new FileLoader(config.schemaCacheDir().resolve(plan.schemaFile()).toFile()).load();
        if (schema == null) {
            throw new IOException("Unable to read JSON Schema from " + plan.schemaFile());
        }
        return schema;
    }

    private void validateRootSchema(Schema schema, GenerationPlan plan) throws GenerationDiagnosticException {
        if (schema.has$ref() && SchemaReferenceCompositionSupport.isExternalRef(schema.get$ref())) {
            throw new GenerationDiagnosticException("UNSUPPORTED_KEYWORD", "Root external $ref is not supported for " + plan.discovered().schema().name());
        }
        if (schema.has$ref() && !SchemaReferenceCompositionSupport.isSupportedLocalRef(schema.get$ref())) {
            throw new GenerationDiagnosticException("UNSUPPORTED_KEYWORD", "Root local $ref outside $defs/definitions is not supported for " + plan.discovered().schema().name());
        }
        if (SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)) {
            throw new GenerationDiagnosticException("UNSUPPORTED_KEYWORD", "Root allOf cannot be flattened deterministically for " + plan.discovered().schema().name());
        }
        if (hasUnsupportedTypeUnion(schema)) {
            throw new GenerationDiagnosticException("UNSUPPORTED_KEYWORD", "Root type union with multiple non-null types is not supported for " + plan.discovered().schema().name());
        }
    }

    private void prepareRootAnyOf(Schema schema) {
        if (!schema.hasAnyOf()) {
            return;
        }
        // SourceGenerator's polymorphic top-level generation is implemented for oneOf. For a
        // discovered root anyOf with object alternatives, reuse that path as a Java type model;
        // property-level anyOf remains handled by TypeAggregator and is not converted here.
        schema.setOneOf(schema.getAnyOf());
        schema.setAnyOf(null);
    }

    private boolean hasUnsupportedTypeUnion(Schema schema) {
        return schema.hasType()
            && schema.getType().stream()
                .filter(type -> !Schema.Type.NULL.equals(type))
                .distinct()
                .count() > 1;
    }

    private void warnIfNonDefaultDialect(Schema schema,
                                         DiscoveredSchemaEntry discovered,
                                         List<JsonSchemaRecordsManifest.Warning> warnings) {
        String schemaUri = blankToNull(schema.get$schema());
        if (schemaUri == null || isDraft202012(schemaUri)) {
            return;
        }
        DiscoveredSchema discoveredSchema = discovered.schema();
        String message = "Schema declares $schema=" + schemaUri + "; processing as Draft 2020-12 best effort";
        warnings.add(new JsonSchemaRecordsManifest.Warning(
            discovered.source().name(),
            discoveredSchema.scope(),
            discoveredSchema.name(),
            DiscoveryStep.GENERATION,
            "SCHEMA_DIALECT",
            message
        ));
        logger.warn(formatWarning(discovered.source().name(), discoveredSchema.scope(), discoveredSchema.name(), DiscoveryStep.GENERATION, "SCHEMA_DIALECT", message));
    }

    private boolean isDraft202012(String schemaUri) {
        String normalized = schemaUri.endsWith("#") ? schemaUri.substring(0, schemaUri.length() - 1) : schemaUri;
        return DRAFT_2020_12_SCHEMA.equals(normalized);
    }

    private Set<String> generatedJavaFiles(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            return Set.of();
        }
        try (var paths = Files.walk(outputDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(path -> relativeJavaFile(outputDir, path))
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private void addGeneratedJavaFiles(Path outputDir, Set<String> beforeGeneration, List<String> generatedJavaFiles, Path plannedOutputFile) throws IOException {
        LinkedHashSet<String> newFiles = new LinkedHashSet<>(generatedJavaFiles(outputDir));
        newFiles.removeAll(beforeGeneration);
        if (Files.exists(plannedOutputFile)) {
            newFiles.add(relativeJavaFile(outputDir, plannedOutputFile));
        }
        for (String newFile : newFiles) {
            if (!generatedJavaFiles.contains(newFile)) {
                generatedJavaFiles.add(newFile);
            }
        }
    }

    private String relativeJavaFile(Path outputDir, Path javaFile) {
        return outputDir.toAbsolutePath().normalize()
            .relativize(javaFile.toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/');
    }

    private String diagnosticCode(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof GenerationDiagnosticException diagnosticException) {
                return diagnosticException.code();
            }
            String message = current.getMessage();
            if (message != null) {
                if (message.contains(NAME_COLLISION)) {
                    return NAME_COLLISION;
                }
                if (message.contains("UNSUPPORTED_KEYWORD")) {
                    return "UNSUPPORTED_KEYWORD";
                }
            }
            current = current.getCause();
        }
        return "GENERATION_FAILED";
    }

    private GenerationPlan planGeneration(JsonSchemaRecordsGeneratorConfig config, String schemaFile, DiscoveredSchemaEntry discovered) {
        String topLevelTypeName = toTypeName(discovered.schema().name());
        String fqcn = config.targetPackage() + "." + topLevelTypeName;
        Path outputFile = config.outputDir()
            .resolve(config.targetPackage().replace('.', '/'))
            .resolve(topLevelTypeName + ".java")
            .normalize();
        return new GenerationPlan(schemaFile, discovered, topLevelTypeName, fqcn, outputFile);
    }

    private Set<GenerationPlan> resolveNameCollisions(JsonSchemaRecordsGeneratorConfig config,
                                                      List<GenerationPlan> generationPlans,
                                                      List<JsonSchemaRecordsManifest.Warning> warnings,
                                                      List<JsonSchemaRecordsManifest.Skipped> skipped) throws IOException {
        Map<String, List<GenerationPlan>> byFqcn = new LinkedHashMap<>();
        Map<String, List<GenerationPlan>> byOutputFile = new LinkedHashMap<>();
        for (GenerationPlan plan : generationPlans) {
            byFqcn.computeIfAbsent(plan.fqcn(), ignored -> new ArrayList<>()).add(plan);
            byOutputFile.computeIfAbsent(plan.outputFile().toString(), ignored -> new ArrayList<>()).add(plan);
        }

        LinkedHashSet<GenerationPlan> collidingPlans = new LinkedHashSet<>();
        List<String> collisionMessages = new ArrayList<>();
        collectCollisions("FQCN", byFqcn, collidingPlans, collisionMessages);
        collectCollisions("SOURCE_FILE", byOutputFile, collidingPlans, collisionMessages);
        if (collidingPlans.isEmpty()) {
            return Set.of();
        }

        String message = NAME_COLLISION + ": discovered schemas resolve to the same generated Java type or source file "
            + "after name sanitization. " + String.join("; ", collisionMessages)
            + ". Use distinct schema names or generate colliding sources into different target packages/output directories.";
        if (!config.skipOnError()) {
            throw new IOException(message);
        }

        for (GenerationPlan plan : collidingPlans) {
            DiscoveredSchemaEntry discovered = plan.discovered();
            DiscoveredSchema schema = discovered.schema();
            warnings.add(new JsonSchemaRecordsManifest.Warning(
                discovered.source().name(),
                schema.scope(),
                schema.name(),
                DiscoveryStep.GENERATION,
                NAME_COLLISION,
                message
            ));
            skipped.add(new JsonSchemaRecordsManifest.Skipped(
                discovered.source().name(),
                schema.scope(),
                schema.name(),
                DiscoveryStep.GENERATION,
                NAME_COLLISION,
                message,
                schema.retrievalMode()
            ));
            logger.warn(formatWarning(discovered.source().name(), schema.scope(), schema.name(), DiscoveryStep.GENERATION, NAME_COLLISION, message));
        }
        return collidingPlans;
    }

    private void collectCollisions(String kind,
                                   Map<String, List<GenerationPlan>> candidates,
                                   Set<GenerationPlan> collidingPlans,
                                   List<String> collisionMessages) {
        candidates.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .forEach(entry -> {
                collidingPlans.addAll(entry.getValue());
                collisionMessages.add(kind + "=" + entry.getKey() + " inputs=[" + describeInputs(entry.getValue()) + "]");
            });
    }

    private String describeInputs(List<GenerationPlan> plans) {
        return plans.stream()
            .map(plan -> {
                DiscoveredSchemaEntry discovered = plan.discovered();
                return "sourceName=" + discovered.source().name()
                    + ", provider=" + discovered.source().provider()
                    + ", name=" + discovered.schema().name()
                    + ", topLevelTypeName=" + plan.topLevelTypeName()
                    + ", fqcn=" + plan.fqcn()
                    + ", outputFile=" + plan.outputFile();
            })
            .collect(java.util.stream.Collectors.joining(" | "));
    }

    private Path writeManifest(JsonSchemaRecordsGeneratorConfig config,
                               Map<String, Map<String, String>> sourceMetadata,
                               List<JsonSchemaRecordsManifest.Warning> warnings,
                               List<JsonSchemaRecordsManifest.Skipped> skipped,
                               List<JsonSchemaRecordsManifest.SchemaFile> schemaEntries,
                               List<String> emittedFiles,
                               List<String> generatedJavaFiles) throws IOException {
        JsonSchemaRecordsManifest manifest = new JsonSchemaRecordsManifest(
            new JsonSchemaRecordsManifest.Generator(GENERATOR_NAME, Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("dev")),
            Instant.now().toString(),
            config.sources().stream()
                .map(source -> new JsonSchemaRecordsManifest.SourceMetadata(
                    source.name(),
                    source.provider(),
                    sanitizeOptions(sourceMetadata.getOrDefault(source.name(), Map.of()))
                ))
                .toList(),
            new JsonSchemaRecordsManifest.Parameters(
                config.targetPackage(),
                config.languageLevel(),
                config.schemaCacheDir().toString(),
                config.outputDir().toString(),
                config.sources().stream()
                    .map(source -> new JsonSchemaRecordsManifest.ConfiguredSource(
                        source.name(),
                        source.provider(),
                        sanitizeOptions(source.options())
                    ))
                    .toList(),
                config.skipOnError(),
                config.failOnMissingSource()
            ),
            new JsonSchemaRecordsManifest.Discovery(schemaEntries),
            warnings,
            skipped,
            emittedFiles,
            generatedJavaFiles
        );
        Path manifestPath = config.schemaCacheDir().resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        return manifestPath;
    }

    private Map<String, String> sourceMetadata(SchemaDiscoveryProvider provider, JsonSchemaRecordsGeneratorConfig config) {
        if (provider.usesJdbc()) {
            return Map.of("jdbcUrlSanitized", sanitizeJdbcUrl(config.jdbcUrl()));
        }
        return Map.of();
    }

    private Map<String, String> mergeSourceMetadata(Map<String, String> initialMetadata, Map<String, String> providerMetadata) {
        if (initialMetadata.isEmpty()) {
            return providerMetadata;
        }
        if (providerMetadata.isEmpty()) {
            return initialMetadata;
        }
        Map<String, String> merged = new LinkedHashMap<>(initialMetadata);
        merged.putAll(providerMetadata);
        return merged;
    }

    private void logEffectiveParameters(JsonSchemaRecordsGeneratorConfig config) {
        logger.info("[jsonschema-records] INFO languageLevel=" + config.languageLevel()
            + " schemaCacheDir=" + config.schemaCacheDir()
            + " outputDir=" + config.outputDir()
            + " skipOnError=" + config.skipOnError()
            + " failOnMissingSource=" + config.failOnMissingSource()
            + " sources=" + config.sources().stream().map(this::describeSource).toList());
    }

    private String describeSource(SourceSpec source) {
        return source.name() + "(" + source.provider() + ", options=" + sanitizeOptions(source.options()) + ")";
    }

    private Map<String, Object> sanitizeOptions(Map<String, ?> options) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        options.forEach((key, val) -> sanitized.put(key, isSensitiveOptionKey(key) ? "<redacted>" : sanitizeOptionValue(val)));
        return sanitized;
    }

    private Object sanitizeOptionValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> sanitized.put(String.valueOf(key),
                isSensitiveOptionKey(String.valueOf(key)) ? "<redacted>" : sanitizeOptionValue(entryValue)));
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(entry -> sanitized.add(sanitizeOptionValue(entry)));
            return sanitized;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> sanitized = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                sanitized.add(sanitizeOptionValue(java.lang.reflect.Array.get(value, i)));
            }
            return sanitized;
        }
        return value;
    }

    private boolean isSensitiveOptionKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ENGLISH);
        return normalized.contains("password")
            || normalized.contains("passwd")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.endsWith("key");
    }

    private RecordAdoptionStrategy recordAdoptionStrategy(int languageLevel) {
        return languageLevel >= 16 ? RecordAdoptionStrategy.PREFER_RECORD : RecordAdoptionStrategy.ALWAYS_CLASS;
    }

    private void writeCanonicalJson(Path outputPath, String jsonSchema) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(jsonSchema);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), jsonNode);
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        String sanitized = jdbcUrl.replaceAll("\\?.*$", "");
        sanitized = sanitized.replaceAll("(jdbc:oracle:thin:)([^@/]+/[^@]+@)", "$1");
        return sanitized;
    }

    private String sanitizeSegment(String value) {
        return sanitizeNamePart(value, false);
    }

    private String sanitizeFileName(String name, String owner) {
        String sanitized = sanitizeNamePart(name, true);
        if (owner != null && !owner.isBlank()) {
            sanitized = trimCacheName(sanitizeNamePart(owner, true) + "_" + sanitized);
        }
        return sanitized + ".schema.json";
    }

    private String sanitizeNamePart(String value, boolean uppercase) {
        String input = value == null ? "" : value.trim();
        input = uppercase ? input.toUpperCase(Locale.ENGLISH) : input.toLowerCase(Locale.ENGLISH);

        StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isAsciiAlphaNumeric(c)) {
                sanitized.append(c);
            } else if (!sanitized.isEmpty() && sanitized.charAt(sanitized.length() - 1) != '_') {
                sanitized.append('_');
            }
        }
        int length = sanitized.length();
        if (length > 0 && sanitized.charAt(length - 1) == '_') {
            sanitized.setLength(length - 1);
        }
        if (sanitized.isEmpty()) {
            sanitized.append("schema");
        }
        return trimCacheName(sanitized.toString());
    }

    private boolean isAsciiAlphaNumeric(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    private String trimCacheName(String value) {
        return value.length() <= MAX_CACHE_NAME_LENGTH ? value : value.substring(0, MAX_CACHE_NAME_LENGTH);
    }

    private Path uniqueSchemaPath(Path sourceDirectory, String fileName, Set<String> usedRelativeFiles) {
        Path candidate = sourceDirectory.resolve(fileName);
        String relativeFile = candidate.toString().replace('\\', '/');
        if (!usedRelativeFiles.contains(relativeFile)) {
            return candidate;
        }
        String suffix = ".schema.json";
        String baseName = fileName.endsWith(suffix) ? fileName.substring(0, fileName.length() - suffix.length()) : fileName;
        int counter = 2;
        do {
            String counterSuffix = "_" + counter;
            String numberedBaseName = baseName.length() + counterSuffix.length() <= MAX_CACHE_NAME_LENGTH
                ? baseName
                : baseName.substring(0, MAX_CACHE_NAME_LENGTH - counterSuffix.length());
            candidate = sourceDirectory.resolve(numberedBaseName + counterSuffix + suffix);
            relativeFile = candidate.toString().replace('\\', '/');
            counter++;
        } while (usedRelativeFiles.contains(relativeFile));
        return candidate;
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

    private String formatWarning(String sourceName, String scope, String name, DiscoveryStep step, String code, String message) {
        return "[jsonschema-records] WARN source=" + sourceName + " scope=" + (scope == null ? "SOURCE" : scope) + " name=" + (name == null ? "-" : name) + " step=" + step + " code=" + code + " msg=\"" + escape(message) + "\"";
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

    private record DiscoveredSchemaEntry(SourceSpec source, DiscoveredSchema schema) {
    }

    private record GenerationPlan(
        String schemaFile,
        DiscoveredSchemaEntry discovered,
        String topLevelTypeName,
        String fqcn,
        Path outputFile
    ) {
    }

    private static final class GenerationDiagnosticException extends Exception {
        private final String code;

        private GenerationDiagnosticException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private final class LazyJdbcConnectionProvider implements JdbcConnectionProvider, AutoCloseable {
        private final JsonSchemaRecordsGeneratorConfig config;
        private java.sql.Connection connection;

        private LazyJdbcConnectionProvider(JsonSchemaRecordsGeneratorConfig config) {
            this.config = config;
        }

        @Override
        public java.sql.Connection getConnection() throws SQLException {
            if (connection == null || connection.isClosed()) {
                if (config.jdbcUrl() == null || config.jdbcUrl().isBlank()) {
                    throw new SQLException("Missing JDBC URL for JDBC-backed schema discovery source.");
                }
                logger.info("[jsonschema-records] INFO jdbcUrl=" + sanitizeJdbcUrl(config.jdbcUrl()));
                connection = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
            }
            return connection;
        }

        @Override
        public void close() throws SQLException {
            if (connection != null) {
                connection.close();
            }
        }
    }
}

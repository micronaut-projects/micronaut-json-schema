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
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.DiscoverySource;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.Scope;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.Step;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.RecordAdoptionStrategy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oracle discovery and Java record generation pipeline.
 *
 * @since 2.0.0
 */
@Internal
public final class OracleJsonSchemaPipeline {

    private static final Pattern VALIDATE_USING_PATTERN = Pattern.compile("(?is)VALIDATE\\s+USING\\s+'((?:''|[^'])*)'");
    private static final String GENERATOR_NAME = "oracle-json-schema-record-generator";
    private final ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper();
    private final OracleJsonSchemaLogger logger;

    /**
     * Create a new pipeline.
     * @param logger The logger to use
     */
    public OracleJsonSchemaPipeline(OracleJsonSchemaLogger logger) {
        this.logger = logger;
    }

    /**
     * Execute discovery and source generation.
     * @param config The generator configuration
     * @return The execution result
     * @throws IOException If file I/O fails
     * @throws SQLException If database access fails
     */
    public Result execute(OracleJsonSchemaGeneratorConfig config) throws IOException, SQLException {
        Set<DiscoverySource> sources = config.resolveSources();
        Files.createDirectories(config.schemaCacheDir());
        Files.createDirectories(config.outputDir());

        List<OracleJsonSchemaManifest.Warning> warnings = new ArrayList<>();
        List<OracleJsonSchemaManifest.Skipped> skipped = new ArrayList<>();
        List<DiscoveredSchema> discoveredSchemas = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            logger.info("[oracle-jsonschema] INFO jdbcUrl=" + sanitizeJdbcUrl(config.jdbcUrl()) + " owner=" + safeOwner(config.owner()));
            if (sources.contains(DiscoverySource.ORACLE_DOMAIN)) {
                discoveredSchemas.addAll(discoverDomains(connection, config, warnings, skipped));
            }
            if (sources.contains(DiscoverySource.ORACLE_JSON_VIEW)) {
                discoveredSchemas.addAll(discoverDualityViews(connection, config, warnings, skipped));
            }
        } catch (SQLException e) {
            if (config.failOnMissingDb()) {
                throw e;
            }
            logger.warn("[oracle-jsonschema] WARN scope=DOMAIN name=* step=DISCOVERY code=DB_UNAVAILABLE msg=\"" + escape(e.getMessage()) + "\"");
            return new Result(null, 0);
        }

        List<OracleJsonSchemaManifest.SchemaFile> domainEntries = new ArrayList<>();
        List<OracleJsonSchemaManifest.SchemaFile> viewEntries = new ArrayList<>();
        List<String> emittedFiles = new ArrayList<>();
        Map<String, DiscoveredSchema> byRelativeFile = new LinkedHashMap<>();

        discoveredSchemas.sort(Comparator.comparing(DiscoveredSchema::name));
        for (DiscoveredSchema schema : discoveredSchemas) {
            Path relativePath = schema.scope() == Scope.DOMAIN
                ? Path.of("domains", schema.fileName())
                : Path.of("duality-views", schema.fileName());
            Path outputPath = config.schemaCacheDir().resolve(relativePath);
            Files.createDirectories(outputPath.getParent());
            writeCanonicalJson(outputPath, schema.jsonSchema());
            emittedFiles.add(relativePath.toString().replace('\\', '/'));
            byRelativeFile.put(relativePath.toString().replace('\\', '/'), schema);

            OracleJsonSchemaManifest.SchemaFile entry = new OracleJsonSchemaManifest.SchemaFile(
                schema.name(),
                relativePath.toString().replace('\\', '/'),
                schema.retrievalSource()
            );
            if (schema.scope() == Scope.DOMAIN) {
                domainEntries.add(entry);
            } else {
                viewEntries.add(entry);
            }
        }

        int generatedTypes = generateSources(config, byRelativeFile, warnings, skipped);
        Path manifestPath = writeManifest(config, sources, warnings, skipped, domainEntries, viewEntries, emittedFiles);
        logger.info("[oracle-jsonschema] INFO generatedTypes=" + generatedTypes + " outputDir=" + config.outputDir());
        return new Result(manifestPath, generatedTypes);
    }

    private List<DiscoveredSchema> discoverDomains(Connection connection,
                                                   OracleJsonSchemaGeneratorConfig config,
                                                   List<OracleJsonSchemaManifest.Warning> warnings,
                                                   List<OracleJsonSchemaManifest.Skipped> skipped) throws SQLException, IOException {
        QueryScope scope = resolveScope(connection, config.owner(), "DOMAINS", warnings, Scope.DOMAIN);
        Set<String> filter = config.includeFilter(DiscoverySource.ORACLE_DOMAIN);
        List<DiscoveredSchema> result = new ArrayList<>();
        String sql = scope.userScope()
            ? "SELECT name FROM " + scope.viewName()
            : "SELECT name FROM " + scope.viewName() + " WHERE owner = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!scope.userScope()) {
                statement.setString(1, config.owner().toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String domainName = rs.getString(1);
                    if (!filter.isEmpty() && !filter.contains(domainName.toUpperCase(Locale.ENGLISH))) {
                        continue;
                    }
                    try {
                        DiscoveryPayload payload = readDomainPayload(connection, scope, domainName, config.owner(), warnings);
                        result.add(new DiscoveredSchema(
                            Scope.DOMAIN,
                            domainName,
                            sanitizeFileName(domainName, config.owner()),
                            payload.jsonSchema(),
                            payload.source()
                        ));
                    } catch (Exception e) {
                        handleFailure(config, warnings, skipped, Scope.DOMAIN, domainName, Step.SCHEMA_RETRIEVAL, "MALFORMED_JSON", e.getMessage(), null);
                    }
                }
            }
        }
        return result;
    }

    private List<DiscoveredSchema> discoverDualityViews(Connection connection,
                                                        OracleJsonSchemaGeneratorConfig config,
                                                        List<OracleJsonSchemaManifest.Warning> warnings,
                                                        List<OracleJsonSchemaManifest.Skipped> skipped) throws SQLException, IOException {
        QueryScope scope = resolveScope(connection, config.owner(), "JSON_DUALITY_VIEWS", warnings, Scope.DUALITY_VIEW);
        Set<String> filter = config.includeFilter(DiscoverySource.ORACLE_JSON_VIEW);
        List<DiscoveredSchema> result = new ArrayList<>();
        String sql = scope.userScope()
            ? "SELECT view_name, json_schema FROM " + scope.viewName()
            : "SELECT view_name, json_schema FROM " + scope.viewName() + " WHERE owner = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!scope.userScope()) {
                statement.setString(1, config.owner().toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String viewName = rs.getString(1);
                    if (!filter.isEmpty() && !filter.contains(viewName.toUpperCase(Locale.ENGLISH))) {
                        continue;
                    }
                    String jsonSchema = rs.getString(2);
                    if (jsonSchema == null || jsonSchema.isBlank()) {
                        handleFailure(config, warnings, skipped, Scope.DUALITY_VIEW, viewName, Step.SCHEMA_RETRIEVAL, "MISSING_JSON_SCHEMA", "JSON_SCHEMA is null or empty", "DUALITY_DB_PROVIDED");
                        continue;
                    }
                    ensureValidJson(jsonSchema);
                    result.add(new DiscoveredSchema(
                        Scope.DUALITY_VIEW,
                        viewName,
                        sanitizeFileName(viewName, config.owner()),
                        jsonSchema,
                        "DUALITY_DB_PROVIDED"
                    ));
                }
            }
        }
        return result;
    }

    private DiscoveryPayload readDomainPayload(Connection connection,
                                               QueryScope scope,
                                               String domainName,
                                               String owner,
                                               List<OracleJsonSchemaManifest.Warning> warnings) throws SQLException, IOException {
        try {
            String ddl = readDomainDdl(connection, domainName, scope.userScope() ? null : owner);
            if (ddl != null) {
                String json = extractJsonLiteral(ddl);
                ensureValidJson(json);
                return new DiscoveryPayload(json, "DOMAIN_DDL");
            }
        } catch (SQLException e) {
            warnings.add(new OracleJsonSchemaManifest.Warning(Scope.DOMAIN, domainName, Step.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", e.getMessage()));
            logger.warn(formatWarning(Scope.DOMAIN, domainName, Step.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", e.getMessage()));
        }
        String searchCondition = readDomainConstraint(connection, scope, domainName, owner);
        if (searchCondition == null || searchCondition.isBlank()) {
            throw new IOException("Unable to obtain JSON schema for domain " + domainName);
        }
        String json = extractJsonLiteral(searchCondition);
        ensureValidJson(json);
        return new DiscoveryPayload(json, "DOMAIN_CONSTRAINTS");
    }

    private String readDomainDdl(Connection connection, String domainName, String owner) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual"
            : "SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?, ?) FROM dual";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, domainName);
            if (owner != null && !owner.isBlank()) {
                statement.setString(2, owner.toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readDomainConstraint(Connection connection, QueryScope scope, String domainName, String owner) throws SQLException {
        String constraintView = scope.viewName().replace("_DOMAINS", "_DOMAIN_CONSTRAINTS");
        String sql = scope.userScope()
            ? "SELECT search_condition FROM " + constraintView + " WHERE domain_name = ?"
            : "SELECT search_condition FROM " + constraintView + " WHERE owner = ? AND domain_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (scope.userScope()) {
                statement.setString(1, domainName);
            } else {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
                statement.setString(2, domainName);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private QueryScope resolveScope(Connection connection, String owner, String suffix, List<OracleJsonSchemaManifest.Warning> warnings, Scope scope) throws SQLException {
        if (owner == null || owner.isBlank()) {
            return new QueryScope("USER_" + suffix, true);
        }
        for (String prefix : List.of("ALL_", "DBA_")) {
            String candidate = prefix + suffix;
            if (isQueryable(connection, candidate, owner)) {
                return new QueryScope(candidate, false);
            }
        }
        warnings.add(new OracleJsonSchemaManifest.Warning(scope, owner, Step.DISCOVERY, "PRIVILEGE_DENIED", "Falling back to USER_ scope"));
        logger.warn(formatWarning(scope, owner, Step.DISCOVERY, "PRIVILEGE_DENIED", "Falling back to USER_ scope"));
        return new QueryScope("USER_" + suffix, true);
    }

    private boolean isQueryable(Connection connection, String viewName, String owner) {
        String sql = "SELECT 1 FROM " + viewName + " WHERE owner = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
            statement.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private int generateSources(OracleJsonSchemaGeneratorConfig config,
                                Map<String, DiscoveredSchema> discoveredSchemas,
                                List<OracleJsonSchemaManifest.Warning> warnings,
                                List<OracleJsonSchemaManifest.Skipped> skipped) throws IOException {
        int generated = 0;
        for (Map.Entry<String, DiscoveredSchema> entry : discoveredSchemas.entrySet()) {
            DiscoveredSchema schema = entry.getValue();
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
                    warnings.add(new OracleJsonSchemaManifest.Warning(schema.scope(), schema.name(), Step.GENERATION, warning.code(), warning.message()));
                    logger.warn(formatWarning(schema.scope(), schema.name(), Step.GENERATION, warning.code(), warning.message()));
                }
            } catch (Exception e) {
                handleFailure(config, warnings, skipped, schema.scope(), schema.name(), Step.GENERATION, "GENERATION_FAILED", e.getMessage(), schema.retrievalSource());
            }
        }
        return generated;
    }

    private Path writeManifest(OracleJsonSchemaGeneratorConfig config,
                               Set<DiscoverySource> sources,
                               List<OracleJsonSchemaManifest.Warning> warnings,
                               List<OracleJsonSchemaManifest.Skipped> skipped,
                               List<OracleJsonSchemaManifest.SchemaFile> domainEntries,
                               List<OracleJsonSchemaManifest.SchemaFile> viewEntries,
                               List<String> emittedFiles) throws IOException {
        OracleJsonSchemaManifest manifest = new OracleJsonSchemaManifest(
            new OracleJsonSchemaManifest.Generator(GENERATOR_NAME, Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("dev")),
            Instant.now().toString(),
            new OracleJsonSchemaManifest.Connection(sanitizeJdbcUrl(config.jdbcUrl()), blankToNull(config.owner())),
            new OracleJsonSchemaManifest.Parameters(
                config.targetPackage(),
                config.schemaCacheDir().toString(),
                config.outputDir().toString(),
                config.includeDomains(),
                config.includeViews(),
                sources.stream().map(DiscoverySource::externalName).toList(),
                config.skipOnError(),
                config.failOnMissingDb()
            ),
            new OracleJsonSchemaManifest.Discovery(domainEntries, viewEntries),
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

    private void ensureValidJson(String jsonSchema) throws IOException {
        objectMapper.readTree(jsonSchema);
    }

    private void handleFailure(OracleJsonSchemaGeneratorConfig config,
                               List<OracleJsonSchemaManifest.Warning> warnings,
                               List<OracleJsonSchemaManifest.Skipped> skipped,
                               Scope scope,
                               String name,
                               Step step,
                               String code,
                               String message,
                               String source) throws IOException {
        if (config.skipOnError()) {
            skipped.add(new OracleJsonSchemaManifest.Skipped(scope, name, step, code, message, source));
            logger.warn(formatWarning(scope, name, step, code, message));
            return;
        }
        throw new IOException(message);
    }

    private String extractJsonLiteral(String text) throws IOException {
        Matcher matcher = VALIDATE_USING_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
        }
        return matcher.group(1).replace("''", "'");
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        String sanitized = jdbcUrl.replaceAll("\\?.*$", "");
        sanitized = sanitized.replaceAll("(jdbc:oracle:thin:)([^@/]+/[^@]+@)", "$1");
        return sanitized;
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

    private String formatWarning(Scope scope, String name, Step step, String code, String message) {
        return "[oracle-jsonschema] WARN scope=" + scope + " name=" + name + " step=" + step + " code=" + code + " msg=\"" + escape(message) + "\"";
    }

    private String escape(String message) {
        return message == null ? "" : message.replace("\"", "'");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeOwner(String owner) {
        return owner == null || owner.isBlank() ? "<current-user>" : owner;
    }

    /**
     * Execution result.
     * @param manifestPath Written manifest path, when one was emitted
     * @param generatedTypes Number of generated top-level types
     */
    public record Result(Path manifestPath, int generatedTypes) {
    }

    private record QueryScope(String viewName, boolean userScope) {
    }

    private record DiscoveryPayload(String jsonSchema, String source) {
    }

    private record DiscoveredSchema(Scope scope, String name, String fileName, String jsonSchema, String retrievalSource) {
    }
}

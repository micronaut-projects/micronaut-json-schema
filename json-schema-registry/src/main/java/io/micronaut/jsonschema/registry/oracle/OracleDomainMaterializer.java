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
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema;
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider;
import io.micronaut.jsonschema.registry.JsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Built-in Oracle JSON domain materializer.
 *
 * @since 2.2.0
 */
@Singleton
public final class OracleDomainMaterializer implements OracleSchemaMaterializer {
    private static final Logger LOG = LoggerFactory.getLogger(OracleDomainMaterializer.class);
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final String TARGET = "oracle.domain";
    private static final int SQL_LITERAL_CHUNK_BYTES = 3_000;

    private final JsonSchemaNormalizer normalizer;
    private final SchemaDiscoveryProvider discoveryProvider;
    private final ObjectMapper objectMapper;

    /**
     * @param normalizer Schema normalizer
     */
    public OracleDomainMaterializer(JsonSchemaNormalizer normalizer) {
        this.normalizer = normalizer;
        this.discoveryProvider = new OracleDomainSchemaDiscoveryProvider();
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
    }

    @Override
    public String representationDescription() {
        return "Self-contained Oracle JSON Domain validation schema with strict or CAST validation mode; remote JSON Schema $ref is not supported";
    }

    @Override
    public Optional<JsonSchemaRegistryOutcome> projectionCompatibility(OracleMaterializationRequest request) {
        try {
            Object schema = objectMapper.readValue(request.candidate().schemaJson(), Object.class);
            Optional<String> remoteRef = firstRemoteReference(schema);
            if (remoteRef.isPresent()) {
                return Optional.of(JsonSchemaRegistryOutcome.failure(
                    request.candidate().logicalSchema(),
                    TARGET,
                    JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                    "Oracle domain projection does not support remote JSON Schema $ref: " + remoteRef.get()
                ));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(JsonSchemaRegistryOutcome.failure(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                e.getMessage()
            ));
        }
    }

    @Override
    public JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception {
        String domainName = normalizeIdentifier(Objects.requireNonNull(request.artifactName(), "Oracle domain materializer requires an artifact name"));
        String owner = normalizeOwner(request.owner());
        if (!request.recordOperation("introspection", () -> domainExists(connection, domainName, request.owner()))) {
            if (request.policyMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
                return JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    TARGET,
                    JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                    "Oracle domain is missing: " + domainName
                );
            }
            if (request.dryRun()) {
                return JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    TARGET,
                    JsonSchemaRegistryOutcomeStatus.CREATED,
                    "[DRY-RUN] would create Oracle domain " + qualifiedName(owner, domainName)
                );
            }
            try {
                request.recordOperation("ddl", () -> {
                    createDomain(connection, owner, domainName, request.candidate().schemaJson(), castMode(request));
                    return null;
                });
            } catch (Exception createFailure) {
                // A second process may have created the domain after the existence check.
                if (request.recordOperation("introspection", () -> domainExists(connection, domainName, request.owner()))) {
                    Optional<DiscoveredSchema> concurrent = request.recordOperation("introspection", () -> readDomain(connection, domainName, owner));
                    if (concurrent.isPresent()
                        && normalizer.equivalent(request.candidate().schemaJson(), concurrent.get().schemaJson())
                        && castMode(request) == concurrent.get().castMode()) {
                        return JsonSchemaRegistryOutcome.ok(
                            request.candidate().logicalSchema(),
                            TARGET,
                            JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                            "Oracle domain was created concurrently: " + qualifiedName(owner, domainName)
                        );
                    }
                    return driftOutcome(request, "Oracle domain was created concurrently but its schema is not equivalent: " + qualifiedName(owner, domainName));
                }
                throw createFailure;
            }
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.CREATED,
                "Created Oracle domain " + qualifiedName(owner, domainName)
            );
        }

        Optional<DiscoveredSchema> current = request.recordOperation("introspection", () -> readDomain(connection, domainName, owner));
        if (current.isEmpty()) {
            return driftOutcome(request, "Oracle domain exists but schema could not be discovered: " + qualifiedName(owner, domainName));
        }
        boolean requestedCastMode = castMode(request);
        if (normalizer.equivalent(request.candidate().schemaJson(), current.get().schemaJson())
            && requestedCastMode == current.get().castMode()) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "Oracle domain is equivalent: " + qualifiedName(owner, domainName)
            );
        }
        return driftOutcome(request, "Oracle domain drift detected: " + qualifiedName(owner, domainName));
    }

    private static String normalizeIdentifier(String identifier) {
        String value = identifier.toUpperCase(Locale.ENGLISH);
        if (!SIMPLE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Oracle domain name must be a simple unquoted identifier: " + identifier);
        }
        return value;
    }

    private static boolean domainExists(Connection connection, String domainName, String owner) throws Exception {
        String sql = owner == null || owner.isBlank()
            ? "SELECT name FROM user_domains WHERE name = ?"
            : "SELECT name FROM all_domains WHERE owner = ? AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (owner == null || owner.isBlank()) {
                statement.setString(1, domainName);
            } else {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
                statement.setString(2, domainName);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Optional<DiscoveredSchema> readDomain(Connection connection, String domainName, String owner) throws Exception {
        DiscoveryResult result = RegistryOracleDiscovery.discover(
            discoveryProvider,
            connection,
            "domains",
            owner,
            Map.of("include", domainName),
            LOG::info
        );
        result.warnings().forEach(warning -> LOG.warn("Oracle domain materializer warning: {}", warning));
        result.skipped().forEach(skipped -> LOG.warn(
            "Oracle domain materializer skipped {}: {} {}",
            skipped.name(),
            skipped.code(),
            skipped.reason()
        ));
        if (result.schemas().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.schemas().get(0));
    }

    private static void createDomain(Connection connection,
                                      String owner,
                                      String domainName,
                                      String schemaJson,
                                      boolean castMode) throws Exception {
        String validationMode = castMode ? "VALIDATE CAST USING " : "VALIDATE USING ";
        String sql = "CREATE DOMAIN " + qualifiedName(owner, domainName) + " AS JSON " + validationMode + clobLiteral(schemaJson);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private static boolean castMode(OracleMaterializationRequest request) {
        return OracleJsonSchemaCastMode.parse(request.options().get("castMode")) == OracleJsonSchemaCastMode.CAST;
    }

    private static JsonSchemaRegistryOutcome driftOutcome(OracleMaterializationRequest request, String message) {
        boolean failure = request.driftMode() == JsonSchemaRegistryDriftMode.FAIL;
        return failure
            ? JsonSchemaRegistryOutcome.failure(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message)
            : JsonSchemaRegistryOutcome.ok(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message);
    }

    private static String normalizeOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return null;
        }
        return normalizeIdentifier(owner);
    }

    private static String qualifiedName(String owner, String domainName) {
        return owner == null ? domainName : owner + "." + domainName;
    }

    private static Optional<String> firstRemoteReference(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refValue && !refValue.startsWith("#")) {
                return Optional.of(refValue);
            }
            for (Object nested : map.values()) {
                Optional<String> remote = firstRemoteReference(nested);
                if (remote.isPresent()) {
                    return remote;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object nested : list) {
                Optional<String> remote = firstRemoteReference(nested);
                if (remote.isPresent()) {
                    return remote;
                }
            }
        }
        return Optional.empty();
    }

    private static String clobLiteral(String value) {
        if (escapedUtf8Length(value) <= SQL_LITERAL_CHUNK_BYTES) {
            return "'" + escapeSqlLiteral(value) + "'";
        }
        StringBuilder builder = new StringBuilder();
        int offset = 0;
        while (offset < value.length()) {
            if (builder.length() > 0) {
                builder.append(" || ");
            }
            int end = nextChunkEnd(value, offset);
            builder.append("to_clob('").append(escapeSqlLiteral(value.substring(offset, end))).append("')");
            offset = end;
        }
        return builder.toString();
    }

    private static int nextChunkEnd(String value, int offset) {
        int end = offset;
        int bytes = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int codePointBytes = codePoint == '\'' ? 2 : utf8Length(codePoint);
            if (end > offset && bytes + codePointBytes > SQL_LITERAL_CHUNK_BYTES) {
                break;
            }
            bytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return end;
    }

    private static int escapedUtf8Length(String value) {
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            bytes += codePoint == '\'' ? 2 : utf8Length(codePoint);
            offset += Character.charCount(codePoint);
        }
        return bytes;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}

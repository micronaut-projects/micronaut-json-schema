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

import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.JsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import jakarta.inject.Singleton;
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
 * @since 2.0.0
 */
@Singleton
public final class OracleDomainMaterializer implements OracleSchemaMaterializer {
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final String TARGET = "oracle.domain";
    private static final int SQL_LITERAL_CHUNK_SIZE = 3_000;

    private final JsonSchemaNormalizer normalizer;
    private final OracleDomainDiscoveryProvider discoveryProvider;
    private final ObjectMapper objectMapper;

    /**
     * @param normalizer Schema normalizer
     */
    public OracleDomainMaterializer(JsonSchemaNormalizer normalizer) {
        this.normalizer = normalizer;
        this.discoveryProvider = new OracleDomainDiscoveryProvider();
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
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
            request.recordOperation("ddl", () -> {
                createDomain(connection, owner, domainName, request.candidate().schemaJson());
                return null;
            });
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.CREATED,
                "Created Oracle domain " + qualifiedName(owner, domainName)
            );
        }

        Optional<OracleDiscoveredSchema> current = request.recordOperation("introspection", () -> readDomain(connection, domainName, owner));
        if (current.isEmpty()) {
            return driftOutcome(request, "Oracle domain exists but schema could not be discovered: " + qualifiedName(owner, domainName));
        }
        if (normalizer.equivalent(request.candidate().schemaJson(), current.get().schemaJson())) {
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

    private Optional<OracleDiscoveredSchema> readDomain(Connection connection, String domainName, String owner) throws Exception {
        OracleDiscoveryResult result = discoveryProvider.discover(
            connection,
            new OracleSourceSpec("domains", OracleDomainDiscoveryProvider.class.getName(), owner, Map.of("include", domainName)),
            true,
            ignored -> {
            }
        );
        if (result.schemas().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.schemas().get(0));
    }

    private static void createDomain(Connection connection, String owner, String domainName, String schemaJson) throws Exception {
        String sql = "CREATE DOMAIN " + qualifiedName(owner, domainName) + " AS JSON VALIDATE USING " + clobLiteral(schemaJson);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
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
        if (value.length() <= SQL_LITERAL_CHUNK_SIZE) {
            return "'" + escapeSqlLiteral(value) + "'";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i += SQL_LITERAL_CHUNK_SIZE) {
            if (builder.length() > 0) {
                builder.append(" || ");
            }
            int end = Math.min(i + SQL_LITERAL_CHUNK_SIZE, value.length());
            builder.append("to_clob('").append(escapeSqlLiteral(value.substring(i, end))).append("')");
        }
        return builder.toString();
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}

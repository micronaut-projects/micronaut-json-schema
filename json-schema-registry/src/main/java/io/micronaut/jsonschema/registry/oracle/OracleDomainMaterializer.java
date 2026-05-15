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

import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.JsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final JsonSchemaNormalizer normalizer;
    private final OracleDomainDiscoveryProvider discoveryProvider;

    /**
     * @param normalizer Schema normalizer
     */
    public OracleDomainMaterializer(JsonSchemaNormalizer normalizer) {
        this.normalizer = normalizer;
        this.discoveryProvider = new OracleDomainDiscoveryProvider();
    }

    @Override
    public JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception {
        String domainName = normalizeIdentifier(Objects.requireNonNull(request.artifactName(), "Oracle domain materializer requires an artifact name"));
        if (!domainExists(connection, domainName, request.owner())) {
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
                    "Dry run: would create Oracle domain " + domainName
                );
            }
            createDomain(connection, domainName, request.candidate().schemaJson());
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.CREATED,
                "Created Oracle domain " + domainName
            );
        }

        OracleDiscoveredSchema current = readDomain(connection, domainName, request.owner());
        if (normalizer.equivalent(request.candidate().schemaJson(), current.schemaJson())) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "Oracle domain is equivalent: " + domainName
            );
        }
        boolean failure = request.driftMode() == JsonSchemaRegistryDriftMode.FAIL;
        String message = "Oracle domain drift detected: " + domainName;
        return failure
            ? JsonSchemaRegistryOutcome.failure(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message)
            : JsonSchemaRegistryOutcome.ok(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message);
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

    private OracleDiscoveredSchema readDomain(Connection connection, String domainName, String owner) throws Exception {
        OracleDiscoveryResult result = discoveryProvider.discover(
            connection,
            new OracleSourceSpec("domains", OracleDomainDiscoveryProvider.class.getName(), owner, Map.of("include", domainName)),
            false,
            ignored -> {
            }
        );
        if (result.schemas().isEmpty()) {
            throw new IllegalStateException("Oracle domain exists but schema could not be discovered: " + domainName);
        }
        return result.schemas().get(0);
    }

    private static void createDomain(Connection connection, String domainName, String schemaJson) throws Exception {
        String sql = "CREATE DOMAIN " + domainName + " AS JSON VALIDATE USING '" + escapeSqlLiteral(schemaJson) + "'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}

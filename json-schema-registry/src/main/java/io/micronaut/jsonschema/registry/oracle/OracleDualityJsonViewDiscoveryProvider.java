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
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySkipped;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryStep;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import tools.jackson.databind.ObjectMapper;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Oracle JSON relational duality view discovery provider.
 *
 * @since 2.0.0
 */
public final class OracleDualityJsonViewDiscoveryProvider implements OracleSchemaDiscoveryProvider {
    private static final ObjectMapper OBJECT_MAPPER = JsonSchemaMapperFactory.createMapper();

    @Override
    public OracleDiscoveryResult discover(Connection connection,
                                          OracleSourceSpec source,
                                          boolean skipOnError,
                                          OracleJsonSchemaLogger logger) throws Exception {
        List<OracleDiscoveredSchema> schemas = new ArrayList<>();
        List<OracleDiscoverySkipped> skipped = new ArrayList<>();
        for (String viewName : selectedViewNames(connection, source)) {
            try {
                String schema = readViewSchema(connection, source.owner(), viewName);
                if (schema == null || schema.isBlank()) {
                    skipped.add(skipped(viewName, "MISSING_SCHEMA", "Oracle duality view JSON schema is missing", null));
                    continue;
                }
                OBJECT_MAPPER.readValue(schema, Object.class);
                logger.info("Oracle duality view schema retrieved: " + viewName);
                schemas.add(new OracleDiscoveredSchema(OracleDiscoveryScope.DUALITY_VIEW, viewName, schema, "duality_view_metadata"));
            } catch (Exception e) {
                if (!skipOnError) {
                    throw e;
                }
                skipped.add(skipped(viewName, "UNREADABLE_SCHEMA", e.getMessage(), e));
            }
        }
        return new OracleDiscoveryResult(schemas, List.of(), skipped);
    }

    private static List<String> selectedViewNames(Connection connection, OracleSourceSpec source) throws SQLException {
        String include = source.options().get("include");
        if (include != null && !include.isBlank()) {
            List<String> names = new ArrayList<>();
            for (String part : include.split(",")) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    names.add(name.toUpperCase(Locale.ENGLISH));
                }
            }
            return names;
        }
        return discoverViewNames(connection, source.owner());
    }

    private static List<String> discoverViewNames(Connection connection, String owner) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT view_name FROM user_json_duality_views ORDER BY view_name"
            : "SELECT view_name FROM all_json_duality_views WHERE owner = ? ORDER BY view_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (owner != null && !owner.isBlank()) {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
                return names;
            }
        }
    }

    private static String readViewSchema(Connection connection, String owner, String viewName) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT json_serialize(json_schema RETURNING CLOB) FROM user_json_duality_views WHERE view_name = ?"
            : "SELECT json_serialize(json_schema RETURNING CLOB) FROM all_json_duality_views WHERE owner = ? AND view_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (owner == null || owner.isBlank()) {
                statement.setString(1, viewName.toUpperCase(Locale.ENGLISH));
            } else {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
                statement.setString(2, viewName.toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Object value = resultSet.getObject(1);
                if (value instanceof Clob clob) {
                    return clob.getSubString(1, Math.toIntExact(clob.length()));
                }
                return value == null ? null : value.toString();
            }
        }
    }

    private static OracleDiscoverySkipped skipped(String viewName, String reason, String message, Exception cause) {
        return new OracleDiscoverySkipped(OracleDiscoveryScope.DUALITY_VIEW, viewName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, reason, message, cause);
    }
}

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
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryWarning;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in Oracle JSON domain discovery provider.
 *
 * @since 2.0.0
 */
public final class OracleDomainDiscoveryProvider implements OracleSchemaDiscoveryProvider {
    private static final Pattern VALIDATE_USING_PATTERN = Pattern.compile("(?is)VALIDATE\\s+USING\\s+'((?:''|[^'])*)'");
    private static final ObjectMapper OBJECT_MAPPER = JsonSchemaMapperFactory.createMapper();

    @Override
    public OracleDiscoveryResult discover(Connection connection,
                                          OracleSourceSpec source,
                                          boolean skipOnError,
                                          OracleJsonSchemaLogger logger) throws Exception {
        List<OracleDiscoveredSchema> schemas = new ArrayList<>();
        List<OracleDiscoveryWarning> warnings = new ArrayList<>();
        List<OracleDiscoverySkipped> skipped = new ArrayList<>();
        for (String domainName : selectedDomainNames(connection, source)) {
            try {
                Optional<String> schema = readDomainSchema(connection, source.owner(), domainName, logger, warnings);
                if (schema.isEmpty()) {
                    skipped.add(skipped(domainName, "MISSING_SCHEMA", "Oracle domain schema could not be read", null));
                    continue;
                }
                parseJson(schema.get());
                schemas.add(new OracleDiscoveredSchema(OracleDiscoveryScope.DOMAIN, domainName, schema.get(), "domain_metadata"));
            } catch (Exception e) {
                if (!skipOnError) {
                    throw e;
                }
                skipped.add(skipped(domainName, "UNREADABLE_SCHEMA", e.getMessage(), e));
            }
        }
        return new OracleDiscoveryResult(schemas, warnings, skipped);
    }

    private static List<String> selectedDomainNames(Connection connection, OracleSourceSpec source) throws SQLException {
        String include = source.options().get("include");
        if (include != null && !include.isBlank()) {
            return splitNames(include);
        }
        String prefix = source.options().getOrDefault("prefix", "");
        return discoverDomainNames(connection, source.owner(), prefix);
    }

    private static List<String> splitNames(String value) {
        List<String> names = new ArrayList<>();
        for (String part : value.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name.toUpperCase(Locale.ENGLISH));
            }
        }
        return names;
    }

    private static List<String> discoverDomainNames(Connection connection, String owner, String prefix) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT name FROM user_domains WHERE name LIKE ? ORDER BY name"
            : "SELECT name FROM all_domains WHERE owner = ? AND name LIKE ? ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String pattern = (prefix == null || prefix.isBlank() ? "" : prefix.toUpperCase(Locale.ENGLISH)) + "%";
            if (owner == null || owner.isBlank()) {
                statement.setString(1, pattern);
            } else {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
                statement.setString(2, pattern);
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

    private static Optional<String> readDomainSchema(Connection connection,
                                                     String owner,
                                                     String domainName,
                                                     OracleJsonSchemaLogger logger,
                                                     List<OracleDiscoveryWarning> warnings) {
        try {
            Optional<String> ddl = readDomainDdl(connection, owner, domainName);
            if (ddl.isPresent()) {
                Optional<String> schema = extractValidateUsing(ddl.get());
                if (schema.isPresent()) {
                    logger.info("Oracle domain schema retrieved with get_ddl: " + domainName);
                    return schema;
                }
                warnings.add(warning(domainName, "Unable to extract VALIDATE USING from DBMS_METADATA DDL"));
            }
        } catch (Exception e) {
            warnings.add(warning(domainName, "DBMS_METADATA domain retrieval failed ("
                + failureCategory(e) + "): " + e.getMessage()));
        }
        try {
            Optional<String> condition = readSearchCondition(connection, owner, domainName);
            if (condition.isPresent()) {
                Optional<String> schema = extractValidateUsing(condition.get()).or(() -> Optional.of(condition.get()));
                parseJson(schema.get());
                logger.info("Oracle domain schema retrieved with search_condition: " + domainName);
                return schema;
            }
        } catch (Exception e) {
            warnings.add(warning(domainName, "SEARCH_CONDITION domain retrieval failed ("
                + failureCategory(e) + "): " + e.getMessage()));
        }
        return Optional.empty();
    }

    private static Optional<String> readDomainDdl(Connection connection, String owner, String domainName) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual"
            : "SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?, ?) FROM dual";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, domainName.toUpperCase(Locale.ENGLISH));
            if (owner != null && !owner.isBlank()) {
                statement.setString(2, owner.toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(readString(resultSet, 1));
            }
        }
    }

    private static Optional<String> readSearchCondition(Connection connection, String owner, String domainName) throws SQLException {
        String sql = owner == null || owner.isBlank()
            ? "SELECT search_condition FROM user_domain_constraints WHERE domain_name = ?"
            : "SELECT search_condition FROM all_domain_constraints WHERE owner = ? AND domain_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (owner == null || owner.isBlank()) {
                statement.setString(1, domainName.toUpperCase(Locale.ENGLISH));
            } else {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
                statement.setString(2, domainName.toUpperCase(Locale.ENGLISH));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(readString(resultSet, 1));
            }
        }
    }

    private static String readString(ResultSet resultSet, int index) throws SQLException {
        Object value = resultSet.getObject(index);
        if (value instanceof Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        return value == null ? null : value.toString();
    }

    private static Optional<String> extractValidateUsing(String value) {
        Matcher matcher = VALIDATE_USING_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).replace("''", "'"));
    }

    private static void parseJson(String schema) throws Exception {
        OBJECT_MAPPER.readValue(schema, Object.class);
    }

    private static String failureCategory(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ENGLISH);
        if (e instanceof SQLException sqlException && isPrivilegeError(sqlException.getErrorCode())) {
            return "likely privilege-related";
        }
        if (message.contains("insufficient privilege")
            || message.contains("ora-01031")
            || message.contains("ora-00942")
            || message.contains("ora-31603")) {
            return "likely privilege-related";
        }
        if (message.contains("parse")
            || message.contains("json")
            || message.contains("unexpected")
            || message.contains("invalid")) {
            return "likely parse-related";
        }
        return "unknown cause";
    }

    private static boolean isPrivilegeError(int errorCode) {
        return errorCode == 1031 || errorCode == 942 || errorCode == 31603;
    }

    private static OracleDiscoveryWarning warning(String domainName, String message) {
        return new OracleDiscoveryWarning(OracleDiscoveryScope.DOMAIN, domainName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, message);
    }

    private static OracleDiscoverySkipped skipped(String domainName, String reason, String message, Exception cause) {
        return new OracleDiscoverySkipped(OracleDiscoveryScope.DOMAIN, domainName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, reason, message, cause);
    }
}

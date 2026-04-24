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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal support utilities shared by the built-in Oracle discovery providers.
 *
 * @since 2.0.0
 */
@Internal
final class OracleDiscoverySupport {

    private static final Pattern VALIDATE_USING_PATTERN = Pattern.compile("(?is)VALIDATE\\s+USING\\s+'((?:''|[^'])*)'");

    private OracleDiscoverySupport() {
    }

    /**
     * Resolve the built-in include filter from a source specification.
     *
     * @param source The configured source
     * @return The normalized include filter entries
     */
    static Set<String> includeFilter(OracleSourceSpec source) {
        return toFilter(parseListOption(source.option("include")));
    }

    /**
     * Resolve the built-in exclude filter from a source specification.
     *
     * @param source The configured source
     * @return The normalized exclude filter entries
     */
    static Set<String> excludeFilter(OracleSourceSpec source) {
        return toFilter(parseListOption(source.option("exclude")));
    }

    /**
     * Match a discovered Oracle object name against built-in include and exclude filters.
     *
     * @param name The discovered object name
     * @param includes The configured include filters
     * @param excludes The configured exclude filters
     * @return {@code true} if the name should be included
     */
    static boolean matches(String name, Set<String> includes, Set<String> excludes) {
        if (!matchesInclude(name, includes)) {
            return false;
        }
        return !matchesExclude(name, excludes);
    }

    /**
     * Resolve the Oracle data dictionary query scope for a built-in provider.
     *
     * @param connection The JDBC connection
     * @param owner The configured owner, if any
     * @param suffix The Oracle data dictionary suffix, for example {@code DOMAINS}
     * @param scope The diagnostic scope
     * @param warnings The warning accumulator
     * @return The resolved metadata query scope
     * @throws SQLException If the dictionary scope cannot be probed
     */
    static MetadataQueryScope resolveScope(Connection connection,
                                           String owner,
                                           String suffix,
                                           OracleDiscoveryScope scope,
                                           List<OracleDiscoveryWarning> warnings) throws SQLException {
        if (owner == null || owner.isBlank()) {
            return new MetadataQueryScope("USER_" + suffix, true);
        }
        for (String prefix : List.of("ALL_", "DBA_")) {
            String candidate = prefix + suffix;
            if (isQueryable(connection, candidate, owner)) {
                return new MetadataQueryScope(candidate, false);
            }
        }
        warnings.add(new OracleDiscoveryWarning(scope, owner, OracleDiscoveryStep.DISCOVERY, "PRIVILEGE_DENIED", "Falling back to USER_ scope"));
        return new MetadataQueryScope("USER_" + suffix, true);
    }

    /**
     * Read the JSON Schema payload for an Oracle SQL domain.
     *
     * @param connection The JDBC connection
     * @param scope The resolved metadata query scope
     * @param domainName The domain name
     * @param owner The configured owner, if any
     * @param warnings The warning accumulator
     * @return The resolved domain payload
     * @throws SQLException If metadata access fails
     * @throws IOException If the schema cannot be extracted or parsed
     */
    static DiscoveryPayload readDomainPayload(Connection connection,
                                              MetadataQueryScope scope,
                                              String domainName,
                                              String owner,
                                              List<OracleDiscoveryWarning> warnings) throws SQLException, IOException {
        try {
            String ddl = readDomainDdl(connection, domainName, scope.currentUserScope() ? null : owner);
            if (ddl != null) {
                String json = extractJsonLiteral(ddl);
                ensureValidJson(json);
                return new DiscoveryPayload(json, "DOMAIN_DDL");
            }
        } catch (SQLException e) {
            warnings.add(new OracleDiscoveryWarning(OracleDiscoveryScope.DOMAIN, domainName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", e.getMessage()));
        }
        String searchCondition = readDomainConstraint(connection, scope, domainName, owner);
        if (searchCondition == null || searchCondition.isBlank()) {
            throw new IOException("Unable to obtain JSON schema for domain " + domainName);
        }
        String json = extractJsonLiteral(searchCondition);
        ensureValidJson(json);
        return new DiscoveryPayload(json, "DOMAIN_CONSTRAINTS");
    }

    /**
     * Validate that the supplied text is parseable JSON.
     *
     * @param jsonSchema The JSON Schema text
     * @throws IOException If the text is not valid JSON
     */
    static void ensureValidJson(String jsonSchema) throws IOException {
        JsonSchemaMapperFactoryHolder.OBJECT_MAPPER.readTree(jsonSchema);
    }

    /**
     * Create a new warning accumulator.
     *
     * @return A mutable warning list
     */
    static List<OracleDiscoveryWarning> warnings() {
        return new ArrayList<>();
    }

    /**
     * Create a new skipped-entry accumulator.
     *
     * @return A mutable skipped-entry list
     */
    static List<OracleDiscoverySkipped> skipped() {
        return new ArrayList<>();
    }

    private static Set<String> toFilter(List<String> values) {
        return new LinkedHashSet<>(values);
    }

    private static List<String> parseListOption(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
            .flatMap(line -> java.util.Arrays.stream(line.split(",")))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .toList();
    }

    private static boolean matchesInclude(String name, Set<String> includes) {
        if (includes.isEmpty()) {
            return true;
        }
        if (includes.contains("*")) {
            return true;
        }
        return matchesConfiguredName(name, includes);
    }

    private static boolean matchesExclude(String name, Set<String> excludes) {
        if (excludes.isEmpty()) {
            return false;
        }
        if (excludes.contains("*")) {
            return true;
        }
        return matchesConfiguredName(name, excludes);
    }

    private static boolean matchesConfiguredName(String name, Set<String> filters) {
        String uppercaseName = name.toUpperCase(Locale.ENGLISH);
        return filters.stream().anyMatch(filter -> filter.equals(name) || filter.toUpperCase(Locale.ENGLISH).equals(uppercaseName));
    }

    private static boolean isQueryable(Connection connection, String viewName, String owner) {
        String sql = "SELECT 1 FROM " + viewName + " WHERE owner = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
            statement.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static String readDomainDdl(Connection connection, String domainName, String owner) throws SQLException {
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

    private static String readDomainConstraint(Connection connection, MetadataQueryScope scope, String domainName, String owner) throws SQLException {
        String constraintView = scope.dictionaryViewName().replace("_DOMAINS", "_DOMAIN_CONSTRAINTS");
        String sql = scope.currentUserScope()
            ? "SELECT search_condition FROM " + constraintView + " WHERE domain_name = ?"
            : "SELECT search_condition FROM " + constraintView + " WHERE owner = ? AND domain_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (scope.currentUserScope()) {
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

    private static String extractJsonLiteral(String text) throws IOException {
        Matcher matcher = VALIDATE_USING_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
        }
        return matcher.group(1).replace("''", "'");
    }

    /**
     * Resolved Oracle data dictionary query scope used by built-in metadata providers.
     *
     * @param dictionaryViewName The Oracle data dictionary view name, for example
     *                           {@code USER_DOMAINS} or {@code ALL_JSON_DUALITY_VIEWS}
     * @param currentUserScope Whether the resolved dictionary access uses current-user scope
     */
    record MetadataQueryScope(String dictionaryViewName, boolean currentUserScope) {
    }

    /**
     * JSON Schema payload discovered from Oracle metadata.
     *
     * @param jsonSchema The JSON Schema document
     * @param source The retrieval source identifier
     */
    record DiscoveryPayload(String jsonSchema, String source) {
    }

    private static final class JsonSchemaMapperFactoryHolder {
        private static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER = io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory.createMapper();
    }
}

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
import io.micronaut.jsonschema.generator.discovery.DiscoverySkipped;
import io.micronaut.jsonschema.generator.discovery.DiscoveryStep;
import io.micronaut.jsonschema.generator.discovery.DiscoveryWarning;
import io.micronaut.jsonschema.generator.discovery.SchemaRetrievalException;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import io.micronaut.jsonschema.generator.discovery.SourceUnavailableException;

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
    static Set<String> includeFilter(SourceSpec source) {
        return toFilter(parseListOption(source.optionValues("include")));
    }

    /**
     * Resolve the built-in exclude filter from a source specification.
     *
     * @param source The configured source
     * @return The normalized exclude filter entries
     */
    static Set<String> excludeFilter(SourceSpec source) {
        return toFilter(parseListOption(source.optionValues("exclude")));
    }

    /**
     * Resolve the Oracle owner option from a source specification.
     *
     * @param source The configured source
     * @return The normalized owner, if configured
     */
    static String owner(SourceSpec source) {
        String owner = source.option("owner");
        return owner == null || owner.isBlank() ? null : normalizeIdentifier(owner);
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
        String normalizedName = normalizeIdentifier(name);
        if (!matchesInclude(normalizedName, includes)) {
            return false;
        }
        return !matchesExclude(normalizedName, excludes);
    }

    /**
     * Normalize a built-in Oracle identifier option or discovered identifier.
     *
     * @param identifier The identifier
     * @return The unquoted uppercase Oracle identifier form
     */
    static String normalizeIdentifier(String identifier) {
        return identifier == null ? null : identifier.trim().toUpperCase(Locale.ENGLISH);
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
                                           List<DiscoveryWarning> warnings) throws SQLException, SourceUnavailableException {
        if (owner == null || owner.isBlank()) {
            return new MetadataQueryScope("USER_" + suffix, true);
        }
        for (String prefix : List.of("ALL_", "DBA_")) {
            String candidate = prefix + suffix;
            if (isQueryable(connection, candidate, owner)) {
                return new MetadataQueryScope(candidate, false);
            }
        }
        String sessionUser = readSessionUser(connection);
        if (matchesOwner(owner, sessionUser)) {
            warnings.add(new DiscoveryWarning(
                scope.name(),
                null,
                DiscoveryStep.DISCOVERY,
                "OWNER_SCOPE_FALLBACK",
                "Cross-schema dictionary views are not queryable; falling back to USER_" + suffix + " because owner matches session user " + sessionUser
            ));
            return new MetadataQueryScope("USER_" + suffix, true);
        }
        throw new SourceUnavailableException(
            scope.name(),
            null,
            DiscoveryStep.DISCOVERY,
            "PRIVILEGE_DENIED",
            "Cross-schema discovery requested for owner " + owner + ", but neither ALL_" + suffix + " nor DBA_" + suffix + " is queryable"
        );
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
                                              List<DiscoveryWarning> warnings) throws SQLException, IOException {
        try {
            String ddl = readDomainDdl(connection, domainName, scope.currentUserScope() ? null : owner);
            if (ddl != null) {
                String json = extractJsonLiteral(ddl);
                ensureValidJson(json);
                return new DiscoveryPayload(json, "DOMAIN_DDL");
            }
        } catch (SQLException | IOException e) {
            warnings.add(new DiscoveryWarning(OracleDiscoveryScope.DOMAIN.name(), domainName, DiscoveryStep.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", e.getMessage()));
        }
        String constraintView = scope.dictionaryViewName().replace("_DOMAINS", "_DOMAIN_CONSTRAINTS");
        if (!isConstraintViewQueryable(connection, constraintView, scope.currentUserScope() ? null : owner)) {
            throw new SchemaRetrievalException(
                "DICTIONARY_VIEW_UNAVAILABLE",
                constraintView + " is not queryable; unable to use DOMAIN_CONSTRAINTS fallback for domain " + domainName,
                null
            );
        }
        String searchCondition;
        try {
            searchCondition = readDomainConstraint(connection, constraintView, scope, domainName, owner);
        } catch (SQLException e) {
            throw new SchemaRetrievalException(
                "DICTIONARY_VIEW_UNAVAILABLE",
                constraintView + " is not queryable; unable to use DOMAIN_CONSTRAINTS fallback for domain " + domainName + ": " + e.getMessage(),
                null
            );
        }
        if (searchCondition == null || searchCondition.isBlank()) {
            throw new SchemaRetrievalException(
                "DOMAIN_CONSTRAINTS_PARSE_FAILED",
                "No JSON VALIDATE constraint found for domain " + domainName,
                "DOMAIN_CONSTRAINTS"
            );
        }
        String json;
        try {
            json = extractJsonLiteral(searchCondition);
        } catch (IOException e) {
            throw new SchemaRetrievalException(
                "DOMAIN_CONSTRAINTS_PARSE_FAILED",
                "Failed to extract JSON schema text from DOMAIN_CONSTRAINTS for domain " + domainName + ": " + e.getMessage(),
                "DOMAIN_CONSTRAINTS"
            );
        }
        try {
            ensureValidJson(json);
        } catch (IOException e) {
            throw new SchemaRetrievalException(
                "MALFORMED_JSON",
                e.getMessage(),
                "DOMAIN_CONSTRAINTS"
            );
        }
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
    static List<DiscoveryWarning> warnings() {
        return new ArrayList<>();
    }

    /**
     * Create a new skipped-entry accumulator.
     *
     * @return A mutable skipped-entry list
     */
    static List<DiscoverySkipped> skipped() {
        return new ArrayList<>();
    }

    private static Set<String> toFilter(List<String> values) {
        LinkedHashSet<String> filters = new LinkedHashSet<>();
        for (String value : values) {
            filters.add(normalizeIdentifier(value));
        }
        return filters;
    }

    private static List<String> parseListOption(List<String> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .flatMap(value -> value.lines())
            .flatMap(line -> java.util.Arrays.stream(line.split(",")))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .toList();
    }

    private static boolean matchesInclude(String name, Set<String> includes) {
        if (includes.isEmpty()) {
            return true;
        }
        return includes.contains(name);
    }

    private static boolean matchesExclude(String name, Set<String> excludes) {
        if (excludes.isEmpty()) {
            return false;
        }
        return excludes.contains(name);
    }

    private static boolean matchesOwner(String owner, String sessionUser) {
        return sessionUser != null && owner.equals(normalizeIdentifier(sessionUser));
    }

    private static String readSessionUser(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM dual");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                String sessionUser = rs.getString(1);
                if (sessionUser != null && !sessionUser.isBlank()) {
                    return sessionUser.trim();
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT USER FROM dual");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
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

    private static boolean isConstraintViewQueryable(Connection connection, String viewName, String owner) {
        String sql = owner == null || owner.isBlank()
            ? "SELECT 1 FROM " + viewName + " FETCH FIRST 1 ROWS ONLY"
            : "SELECT 1 FROM " + viewName + " WHERE domain_owner = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (owner != null && !owner.isBlank()) {
                statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
            }
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

    private static String readDomainConstraint(Connection connection, String constraintView, MetadataQueryScope scope, String domainName, String owner) throws SQLException {
        String sql = scope.currentUserScope()
            ? "SELECT search_condition FROM " + constraintView + " WHERE domain_name = ?"
            : "SELECT search_condition FROM " + constraintView + " WHERE domain_owner = ? AND domain_name = ?";
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
     * @param retrievalMode The retrieval mode identifier
     */
    record DiscoveryPayload(String jsonSchema, String retrievalMode) {
    }

    private static final class JsonSchemaMapperFactoryHolder {
        private static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER = io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory.createMapper();
    }
}

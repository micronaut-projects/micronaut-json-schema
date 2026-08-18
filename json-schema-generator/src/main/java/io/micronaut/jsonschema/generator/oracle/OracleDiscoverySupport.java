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
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;

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
 * @since 2.2.0
 */
@Internal
final class OracleDiscoverySupport {

    private static final String SELECT_ONE_FROM = "SELECT 1 FROM ";
    private static final Pattern VALIDATE_USING_PATTERN = Pattern.compile("(?is)VALIDATE\\s+(CAST\\s+)?USING\\s+");
    private static final Pattern SQL_LITERAL_PART_PATTERN = Pattern.compile("(?is)\\s*(?:TO_CLOB\\s*\\(\\s*'((?:''|[^'])*+)'\\s*\\)|'((?:''|[^'])*+)')");
    private static final Pattern CONCATENATION_PATTERN = Pattern.compile("\\s*\\|\\|\\s*");
    private static final Set<String> ALLOWED_DICTIONARY_VIEWS = Set.of(
        "USER_DOMAINS",
        "ALL_DOMAINS",
        "DBA_DOMAINS",
        "USER_DOMAIN_CONSTRAINTS",
        "ALL_DOMAIN_CONSTRAINTS",
        "DBA_DOMAIN_CONSTRAINTS",
        "USER_JSON_DUALITY_VIEWS",
        "ALL_JSON_DUALITY_VIEWS",
        "DBA_JSON_DUALITY_VIEWS"
    );

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
     * Resolve the optional name prefix filter from a source specification.
     *
     * @param source The configured source
     * @return The normalized prefix, or an empty string
     */
    static String prefix(SourceSpec source) {
        String prefix = source.option("prefix");
        return prefix == null || prefix.isBlank() ? "" : normalizeIdentifier(prefix);
    }

    /**
     * Match a discovered Oracle object name against built-in include and exclude filters.
     *
     * @param name The discovered object name
     * @param includes The configured include filters
     * @param excludes The configured exclude filters
     * @param prefix The configured name prefix
     * @return {@code true} if the name should be included
     */
    static boolean matches(String name, Set<String> includes, Set<String> excludes, String prefix) {
        String normalizedName = normalizeIdentifier(name);
        if (!matchesInclude(normalizedName, includes)) {
            return false;
        }
        return !matchesExclude(normalizedName, excludes)
            && (prefix == null || prefix.isBlank() || normalizedName.startsWith(prefix));
    }

    /**
     * Build an object-list query with owner and include/exclude filters pushed into SQL.
     *
     * @param scope The resolved dictionary query scope
     * @param selectList The columns to select
     * @param nameColumn The dictionary column containing the object name
     * @param owner The normalized owner for cross-schema queries
     * @param includes The normalized include filters
     * @param excludes The normalized exclude filters
     * @param prefix The normalized name prefix filter
     * @return The SQL query and bind parameters
     */
    static FilteredQuery objectListQuery(MetadataQueryScope scope,
                                         String selectList,
                                         String nameColumn,
                                         String owner,
                                         Set<String> includes,
                                         Set<String> excludes,
                                         String prefix) {
        StringBuilder sql = new StringBuilder("SELECT ")
            .append(selectList)
            .append(" FROM ")
            .append(scope.dictionaryViewName());
        List<String> conditions = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        if (!scope.currentUserScope()) {
            conditions.add("owner = ?");
            parameters.add(normalizeIdentifier(owner));
        }
        if (!includes.isEmpty()) {
            conditions.add(nameColumn + " IN (" + placeholders(includes.size()) + ")");
            parameters.addAll(includes);
        }
        if (!excludes.isEmpty()) {
            conditions.add(nameColumn + " NOT IN (" + placeholders(excludes.size()) + ")");
            parameters.addAll(excludes);
        }
        if (prefix != null && !prefix.isBlank()) {
            conditions.add(nameColumn + " LIKE ?");
            parameters.add(prefix + "%");
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY ").append(nameColumn);
        return new FilteredQuery(sql.toString(), parameters);
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
        String ddl = null;
        try {
            ddl = readDomainDdl(connection, domainName, scope.currentUserScope() ? null : owner);
        } catch (SQLException e) {
            warnings.add(new DiscoveryWarning(OracleDiscoveryScope.DOMAIN.name(), domainName, DiscoveryStep.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", "Oracle metadata lookup failed: " + e.getMessage()));
        }
        if (ddl != null) {
            try {
                ExtractedSchema extracted = extractJsonLiteral(ddl);
                ensureValidJson(extracted.jsonSchema());
                return new DiscoveryPayload(extracted.jsonSchema(), "DOMAIN_DDL", extracted.castMode());
            } catch (IOException e) {
                warnings.add(new DiscoveryWarning(OracleDiscoveryScope.DOMAIN.name(), domainName, DiscoveryStep.SCHEMA_RETRIEVAL, "GET_DDL_FAILED", "Oracle metadata JSON could not be parsed: " + e.getMessage()));
            }
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
        try {
            ExtractedSchema extracted = extractJsonLiteral(searchCondition);
            ensureValidJson(extracted.jsonSchema());
            return new DiscoveryPayload(extracted.jsonSchema(), "DOMAIN_CONSTRAINTS", extracted.castMode());
        } catch (IOException e) {
            throw new SchemaRetrievalException(
                "DOMAIN_CONSTRAINTS_PARSE_FAILED",
                "Failed to extract JSON schema text from DOMAIN_CONSTRAINTS for domain " + domainName + ": " + e.getMessage(),
                "DOMAIN_CONSTRAINTS"
            );
        }
    }

    /**
     * Validate that the supplied text is parseable JSON.
     *
     * @param jsonSchema The JSON Schema text
     * @throws IOException If the text is not valid JSON
     */
    static void ensureValidJson(String jsonSchema) throws IOException {
        JsonMapperHolder.JSON_MAPPER.readValue(jsonSchema, JsonNode.class);
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
            .filter(val -> val != null && !val.isBlank())
            .flatMap(val -> val.lines())
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

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
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
        String safeViewName = validatedDictionaryViewName(viewName);
        String sql = SELECT_ONE_FROM + safeViewName + " WHERE owner = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toUpperCase(Locale.ENGLISH));
            statement.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean isConstraintViewQueryable(Connection connection, String viewName, String owner) {
        String safeViewName = validatedDictionaryViewName(viewName);
        String sql = owner == null || owner.isBlank()
            ? SELECT_ONE_FROM + safeViewName + " FETCH FIRST 1 ROWS ONLY"
            : SELECT_ONE_FROM + safeViewName + " WHERE domain_owner = ? FETCH FIRST 1 ROWS ONLY";
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
        String safeConstraintView = validatedDictionaryViewName(constraintView);
        String sql = scope.currentUserScope()
            ? "SELECT search_condition FROM " + safeConstraintView + " WHERE domain_name = ?"
            : "SELECT search_condition FROM " + safeConstraintView + " WHERE domain_owner = ? AND domain_name = ?";
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

    private static String validatedDictionaryViewName(String viewName) {
        if (!ALLOWED_DICTIONARY_VIEWS.contains(viewName)) {
            throw new IllegalArgumentException("Unsupported Oracle dictionary view: " + viewName);
        }
        return viewName;
    }

    private static ExtractedSchema extractJsonLiteral(String text) throws IOException {
        Matcher clause = VALIDATE_USING_PATTERN.matcher(text);
        if (!clause.find()) {
            throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
        }
        StringBuilder jsonSchema = new StringBuilder();
        int cursor = clause.end();
        boolean foundLiteral = false;
        while (true) {
            Matcher literal = SQL_LITERAL_PART_PATTERN.matcher(text);
            literal.region(cursor, text.length());
            if (!literal.lookingAt()) {
                break;
            }
            foundLiteral = true;
            String chunk = literal.group(1) == null ? literal.group(2) : literal.group(1);
            jsonSchema.append(chunk.replace("''", "'"));
            cursor = literal.end();

            Matcher concatenation = CONCATENATION_PATTERN.matcher(text);
            concatenation.region(cursor, text.length());
            if (!concatenation.lookingAt()) {
                break;
            }
            cursor = concatenation.end();
        }
        if (!foundLiteral) {
            throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
        }
        return new ExtractedSchema(jsonSchema.toString(), clause.group(1) != null);
    }

    /**
     * Resolved Oracle data dictionary query scope used by built-in metadata providers.
     *
     * @param dictionaryViewName The Oracle data dictionary view name, for example
     *                           {@code USER_DOMAINS} or {@code ALL_JSON_DUALITY_VIEWS}
     * @param currentUserScope Whether the resolved dictionary access uses current-user scope
     */
    record MetadataQueryScope(String dictionaryViewName, boolean currentUserScope) {
        MetadataQueryScope {
            dictionaryViewName = validatedDictionaryViewName(dictionaryViewName);
        }
    }

    /**
     * SQL query with ordered bind parameters.
     *
     * @param sql The SQL statement
     * @param parameters The ordered bind parameter values
     */
    record FilteredQuery(String sql, List<String> parameters) {

        void bind(PreparedStatement statement) throws SQLException {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setString(i + 1, parameters.get(i));
            }
        }
    }

    /**
     * JSON Schema payload discovered from Oracle metadata.
     *
     * @param jsonSchema The JSON Schema document
     * @param retrievalMode The retrieval mode identifier
     */
    record DiscoveryPayload(String jsonSchema, String retrievalMode, boolean castMode) {
    }

    private record ExtractedSchema(String jsonSchema, boolean castMode) {
    }

    private static final class JsonMapperHolder {
        private static final JsonMapper JSON_MAPPER = JsonMapper.createDefault();
    }
}

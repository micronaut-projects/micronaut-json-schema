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

    private static final Pattern VALIDATE_USING_PATTERN = Pattern.compile("(?is)\\bVALIDATE\\s+USING\\b");

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
     * Resolve the built-in prefix filter from a source specification.
     *
     * @param source The configured source
     * @return The normalized prefix filter entries
     */
    static Set<String> prefixFilter(OracleSourceSpec source) {
        return toFilter(parseListOption(source.option("prefix")));
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
        return matches(name, includes, excludes, Set.of());
    }

    /**
     * Match a discovered Oracle object name against built-in include, exclude, and prefix filters.
     *
     * @param name The discovered object name
     * @param includes The configured include filters
     * @param excludes The configured exclude filters
     * @param prefixes The configured prefix filters
     * @return {@code true} if the name should be included
     */
    static boolean matches(String name, Set<String> includes, Set<String> excludes, Set<String> prefixes) {
        if (!matchesInclude(name, includes)) {
            return false;
        }
        if (!matchesPrefix(name, prefixes)) {
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
                                              List<OracleDiscoveryWarning> warnings,
                                              OracleJsonSchemaLogger logger) throws SQLException, IOException {
        try {
            String ddl = readDomainDdl(connection, domainName, scope.currentUserScope() ? null : owner);
            if (ddl != null) {
                String json = extractJsonLiteral(ddl);
                ensureValidJson(json);
                logger.info("Oracle domain " + domainName + " schema extracted via get_ddl");
                return new DiscoveryPayload(json, "DOMAIN_DDL");
            }
        } catch (SQLException | IOException e) {
            String code = e instanceof SQLException ? "GET_DDL_FAILED" : "GET_DDL_EXTRACTION_FAILED";
            warnings.add(new OracleDiscoveryWarning(OracleDiscoveryScope.DOMAIN, domainName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, code, e.getMessage()));
            logger.warn("Oracle domain " + domainName + " get_ddl extraction failed (" + failureKind(e) + "): " + e.getMessage());
        }
        try {
            String searchCondition = readDomainConstraint(connection, scope, domainName, owner);
            if (searchCondition == null || searchCondition.isBlank()) {
                throw new IOException("Unable to obtain JSON schema for domain " + domainName);
            }
            String json = extractJsonLiteral(searchCondition);
            ensureValidJson(json);
            logger.info("Oracle domain " + domainName + " schema extracted via search_condition");
            return new DiscoveryPayload(json, "DOMAIN_CONSTRAINTS");
        } catch (SQLException | IOException e) {
            logger.warn("Oracle domain " + domainName + " search_condition extraction failed (" + failureKind(e) + "): " + e.getMessage());
            throw e;
        }
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

    private static boolean matchesPrefix(String name, Set<String> prefixes) {
        if (prefixes.isEmpty()) {
            return true;
        }
        String uppercaseName = name.toUpperCase(Locale.ENGLISH);
        return prefixes.stream()
            .anyMatch(prefix -> name.startsWith(prefix) || uppercaseName.startsWith(prefix.toUpperCase(Locale.ENGLISH)));
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
        return parseValidateUsingExpression(text, matcher.end());
    }

    private static String parseValidateUsingExpression(String text, int offset) throws IOException {
        StringBuilder value = new StringBuilder();
        int position = offset;
        boolean found = false;
        while (position < text.length()) {
            position = skipWhitespace(text, position);
            boolean clobWrapper = startsWithIgnoreCase(text, position, "to_clob");
            if (clobWrapper) {
                position = skipWhitespace(text, position + "to_clob".length());
                if (position >= text.length() || text.charAt(position) != '(') {
                    throw new IOException("Failed to parse to_clob JSON schema literal from Oracle metadata.");
                }
                position = skipWhitespace(text, position + 1);
            }
            if (position >= text.length() || text.charAt(position) != '\'') {
                if (found) {
                    break;
                }
                throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
            }
            LiteralResult literal = parseSqlStringLiteral(text, position);
            value.append(literal.value());
            position = literal.end();
            if (clobWrapper) {
                position = skipWhitespace(text, position);
                if (position >= text.length() || text.charAt(position) != ')') {
                    throw new IOException("Failed to parse to_clob JSON schema literal from Oracle metadata.");
                }
                position++;
            }
            found = true;
            position = skipWhitespace(text, position);
            if (position + 1 >= text.length() || text.charAt(position) != '|' || text.charAt(position + 1) != '|') {
                break;
            }
            position += 2;
        }
        if (!found) {
            throw new IOException("Failed to extract JSON schema text from Oracle metadata.");
        }
        return value.toString();
    }

    private static LiteralResult parseSqlStringLiteral(String text, int offset) throws IOException {
        StringBuilder value = new StringBuilder();
        int position = offset + 1;
        while (position < text.length()) {
            char current = text.charAt(position);
            if (current == '\'') {
                if (position + 1 < text.length() && text.charAt(position + 1) == '\'') {
                    value.append('\'');
                    position += 2;
                } else {
                    return new LiteralResult(value.toString(), position + 1);
                }
            } else {
                value.append(current);
                position++;
            }
        }
        throw new IOException("Unterminated JSON schema literal in Oracle metadata.");
    }

    private static int skipWhitespace(String text, int offset) {
        int position = offset;
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean startsWithIgnoreCase(String text, int offset, String prefix) {
        return offset >= 0
            && offset + prefix.length() <= text.length()
            && text.regionMatches(true, offset, prefix, 0, prefix.length());
    }

    private static String failureKind(Exception exception) {
        return exception instanceof SQLException ? "privilege-or-sql" : "parse-or-extraction";
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

    private record LiteralResult(String value, int end) {
    }

    private static final class JsonSchemaMapperFactoryHolder {
        private static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER = io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory.createMapper();
    }
}

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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.DiscoveryPayload;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.MetadataQueryScope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Discovery provider for Oracle SQL domains with JSON validation metadata.
 *
 * @since 2.0.0
 */
@Introspected
public final class OracleDomainDiscoveryProvider implements OracleSchemaDiscoveryProvider {

    @Override
    public OracleDiscoveryResult discover(Connection connection,
                                          OracleSourceSpec source,
                                          boolean skipOnError,
                                          OracleJsonSchemaLogger logger) throws Exception {
        List<OracleDiscoveryWarning> warnings = OracleDiscoverySupport.warnings();
        List<OracleDiscoverySkipped> skipped = OracleDiscoverySupport.skipped();
        MetadataQueryScope scope = OracleDiscoverySupport.resolveScope(connection, source.owner(), "DOMAINS", OracleDiscoveryScope.DOMAIN, warnings);
        Set<String> includes = OracleDiscoverySupport.includeFilter(source);
        Set<String> excludes = OracleDiscoverySupport.excludeFilter(source);
        Set<String> prefixes = OracleDiscoverySupport.prefixFilter(source);
        List<OracleDiscoveredSchema> schemas = new ArrayList<>();
        String sql = scope.currentUserScope()
            ? "SELECT name FROM " + scope.dictionaryViewName()
            : "SELECT name FROM " + scope.dictionaryViewName() + " WHERE owner = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!scope.currentUserScope()) {
                statement.setString(1, source.owner().toUpperCase(java.util.Locale.ENGLISH));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String domainName = rs.getString(1);
                    if (!OracleDiscoverySupport.matches(domainName, includes, excludes, prefixes)) {
                        continue;
                    }
                    try {
                        DiscoveryPayload payload = OracleDiscoverySupport.readDomainPayload(connection, scope, domainName, source.owner(), warnings, logger);
                        schemas.add(new OracleDiscoveredSchema(OracleDiscoveryScope.DOMAIN, domainName, payload.jsonSchema(), payload.source()));
                    } catch (Exception e) {
                        if (skipOnError) {
                            skipped.add(new OracleDiscoverySkipped(OracleDiscoveryScope.DOMAIN, domainName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, "MALFORMED_JSON", e.getMessage(), null));
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
        return new OracleDiscoveryResult(schemas, warnings, skipped);
    }
}

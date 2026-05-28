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

import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.MetadataQueryScope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Discovery provider for Oracle JSON relational duality views.
 *
 * @since 2.0.0
 */
public final class OracleDualityJsonViewDiscoveryProvider implements OracleSchemaDiscoveryProvider {

    @Override
    public OracleDiscoveryResult discover(Connection connection,
                                          OracleSourceSpec source,
                                          boolean skipOnError,
                                          OracleJsonSchemaLogger logger) throws Exception {
        List<OracleDiscoveryWarning> warnings = OracleDiscoverySupport.warnings();
        List<OracleDiscoverySkipped> skipped = OracleDiscoverySupport.skipped();
        MetadataQueryScope scope = OracleDiscoverySupport.resolveScope(connection, source.owner(), "JSON_DUALITY_VIEWS", OracleDiscoveryScope.DUALITY_VIEW, warnings);
        Set<String> includes = OracleDiscoverySupport.includeFilter(source);
        Set<String> excludes = OracleDiscoverySupport.excludeFilter(source);
        List<OracleDiscoveredSchema> schemas = new ArrayList<>();
        String sql = scope.currentUserScope()
            ? "SELECT view_name, json_schema FROM " + scope.dictionaryViewName()
            : "SELECT view_name, json_schema FROM " + scope.dictionaryViewName() + " WHERE owner = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!scope.currentUserScope()) {
                statement.setString(1, source.owner().toUpperCase(java.util.Locale.ENGLISH));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String viewName = rs.getString(1);
                    if (!OracleDiscoverySupport.matches(viewName, includes, excludes)) {
                        continue;
                    }
                    String jsonSchema = rs.getString(2);
                    try {
                        if (jsonSchema == null || jsonSchema.isBlank()) {
                            throw new java.io.IOException("JSON_SCHEMA is null or empty");
                        }
                        OracleDiscoverySupport.ensureValidJson(jsonSchema);
                        schemas.add(new OracleDiscoveredSchema(OracleDiscoveryScope.DUALITY_VIEW, viewName, jsonSchema, "DUALITY_DB_PROVIDED"));
                    } catch (Exception e) {
                        if (skipOnError) {
                            String code = jsonSchema == null || jsonSchema.isBlank() ? "MISSING_JSON_SCHEMA" : "MALFORMED_JSON";
                            skipped.add(new OracleDiscoverySkipped(OracleDiscoveryScope.DUALITY_VIEW, viewName, OracleDiscoveryStep.SCHEMA_RETRIEVAL, code, e.getMessage(), "DUALITY_DB_PROVIDED"));
                            continue;
                        }
                        throw e;
                    }
                }
            }
        }
        return new OracleDiscoveryResult(schemas, warnings, skipped);
    }
}

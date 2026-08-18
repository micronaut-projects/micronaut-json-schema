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

import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema;
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult;
import io.micronaut.jsonschema.generator.discovery.DiscoverySkipped;
import io.micronaut.jsonschema.generator.discovery.DiscoveryStep;
import io.micronaut.jsonschema.generator.discovery.DiscoveryWarning;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import io.micronaut.jsonschema.generator.discovery.SourceUnavailableException;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.FilteredQuery;
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
 * @since 2.2.0
 */
public final class OracleDualityViewSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    public static final String PROVIDER_ID = "oracle-duality-views";
    private static final String DUALITY_DB_PROVIDED = "DUALITY_DB_PROVIDED";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean usesJdbc() {
        return true;
    }

    @Override
    public String sourceScope() {
        return OracleDiscoveryScope.DUALITY_VIEW.name();
    }

    @Override
    public DiscoveryResult discover(SchemaDiscoveryContext context,
                                    SourceSpec source) throws Exception {
        Connection connection = context.requireJdbcConnectionProvider().getConnection();
        boolean skipOnError = context.skipOnError();
        List<DiscoveryWarning> warnings = OracleDiscoverySupport.warnings();
        List<DiscoverySkipped> skipped = OracleDiscoverySupport.skipped();
        String owner = OracleDiscoverySupport.owner(source);
        MetadataQueryScope scope = OracleDiscoverySupport.resolveScope(connection, owner, "JSON_DUALITY_VIEWS", OracleDiscoveryScope.DUALITY_VIEW, warnings);
        context.logger().info("[jsonschema-records] INFO source=" + source.name() + " scope=" + OracleDiscoveryScope.DUALITY_VIEW.name() + " dictionaryView=" + scope.dictionaryViewName());
        Set<String> includes = OracleDiscoverySupport.includeFilter(source);
        Set<String> excludes = OracleDiscoverySupport.excludeFilter(source);
        String prefix = OracleDiscoverySupport.prefix(source);
        List<DiscoveredSchema> schemas = new ArrayList<>();
        int selectedInputs = 0;
        FilteredQuery query = OracleDiscoverySupport.objectListQuery(
            scope,
            "view_name, json_schema",
            "view_name",
            owner,
            includes,
            excludes,
            prefix
        );
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            query.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String viewName = OracleDiscoverySupport.normalizeIdentifier(rs.getString(1));
                    if (!OracleDiscoverySupport.matches(viewName, includes, excludes, prefix)) {
                        continue;
                    }
                    selectedInputs++;
                    String jsonSchema = rs.getString(2);
                    if (jsonSchema == null || jsonSchema.isBlank()) {
                        if (skipOnError) {
                            skipped.add(new DiscoverySkipped(OracleDiscoveryScope.DUALITY_VIEW.name(), viewName, DiscoveryStep.SCHEMA_RETRIEVAL, "MISSING_JSON_SCHEMA", "JSON_SCHEMA is null or empty", DUALITY_DB_PROVIDED));
                            continue;
                        }
                        throw new java.io.IOException("JSON_SCHEMA is null or empty for duality view " + viewName);
                    }
                    try {
                        OracleDiscoverySupport.ensureValidJson(jsonSchema);
                        schemas.add(new DiscoveredSchema(OracleDiscoveryScope.DUALITY_VIEW.name(), viewName, jsonSchema, DUALITY_DB_PROVIDED));
                        context.logger().info("[jsonschema-records] INFO source=" + source.name() + " scope=" + OracleDiscoveryScope.DUALITY_VIEW.name() + " name=" + viewName + " retrievalMode=" + DUALITY_DB_PROVIDED);
                    } catch (Exception e) {
                        if (skipOnError) {
                            skipped.add(new DiscoverySkipped(OracleDiscoveryScope.DUALITY_VIEW.name(), viewName, DiscoveryStep.SCHEMA_RETRIEVAL, "MALFORMED_JSON", e.getMessage(), DUALITY_DB_PROVIDED));
                            continue;
                        }
                        throw e;
                    }
                }
            }
        }
        if (selectedInputs == 0) {
            String message = "No duality views matched include/exclude filters";
            if (context.failOnMissingSource()) {
                throw new SourceUnavailableException(OracleDiscoveryScope.DUALITY_VIEW.name(), null, DiscoveryStep.DISCOVERY, "NO_INPUTS_DISCOVERED", message);
            }
            warnings.add(new DiscoveryWarning(OracleDiscoveryScope.DUALITY_VIEW.name(), null, DiscoveryStep.DISCOVERY, "NO_INPUTS_DISCOVERED", message));
        }
        return new DiscoveryResult(schemas, warnings, skipped, context.sourceMetadata());
    }
}

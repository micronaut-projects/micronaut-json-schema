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
import io.micronaut.jsonschema.generator.discovery.SchemaRetrievalException;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import io.micronaut.jsonschema.generator.discovery.SourceUnavailableException;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.DiscoveryPayload;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySupport.FilteredQuery;
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
 * @since 2.2.0
 */
public final class OracleDomainSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    public static final String PROVIDER_ID = "oracle-domains";

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
        return OracleDiscoveryScope.DOMAIN.name();
    }

    @Override
    public DiscoveryResult discover(SchemaDiscoveryContext context,
                                    SourceSpec source) throws Exception {
        Connection connection = context.requireJdbcConnectionProvider().getConnection();
        boolean skipOnError = context.skipOnError();
        List<DiscoveryWarning> warnings = OracleDiscoverySupport.warnings();
        List<DiscoverySkipped> skipped = OracleDiscoverySupport.skipped();
        String owner = OracleDiscoverySupport.owner(source);
        MetadataQueryScope scope = OracleDiscoverySupport.resolveScope(connection, owner, "DOMAINS", OracleDiscoveryScope.DOMAIN, warnings);
        context.logger().info("[jsonschema-records] INFO source=" + source.name() + " scope=" + OracleDiscoveryScope.DOMAIN.name() + " dictionaryView=" + scope.dictionaryViewName());
        Set<String> includes = OracleDiscoverySupport.includeFilter(source);
        Set<String> excludes = OracleDiscoverySupport.excludeFilter(source);
        List<DiscoveredSchema> schemas = new ArrayList<>();
        int selectedInputs = 0;
        FilteredQuery query = OracleDiscoverySupport.objectListQuery(scope, "name", "name", owner, includes, excludes);
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            query.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String domainName = OracleDiscoverySupport.normalizeIdentifier(rs.getString(1));
                    if (!OracleDiscoverySupport.matches(domainName, includes, excludes)) {
                        continue;
                    }
                    selectedInputs++;
                    try {
                        DiscoveryPayload payload = OracleDiscoverySupport.readDomainPayload(connection, scope, domainName, owner, warnings);
                        schemas.add(new DiscoveredSchema(OracleDiscoveryScope.DOMAIN.name(), domainName, payload.jsonSchema(), payload.retrievalMode()));
                        context.logger().info("[jsonschema-records] INFO source=" + source.name() + " scope=" + OracleDiscoveryScope.DOMAIN.name() + " name=" + domainName + " retrievalMode=" + payload.retrievalMode());
                    } catch (SchemaRetrievalException e) {
                        if (skipOnError) {
                            skipped.add(new DiscoverySkipped(OracleDiscoveryScope.DOMAIN.name(), domainName, DiscoveryStep.SCHEMA_RETRIEVAL, e.code(), e.getMessage(), e.retrievalMode()));
                        } else {
                            throw e;
                        }
                    } catch (Exception e) {
                        if (skipOnError) {
                            skipped.add(new DiscoverySkipped(OracleDiscoveryScope.DOMAIN.name(), domainName, DiscoveryStep.SCHEMA_RETRIEVAL, "MALFORMED_JSON", e.getMessage(), null));
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
        if (selectedInputs == 0) {
            String message = "No domains matched include/exclude filters";
            if (context.failOnMissingSource()) {
                throw new SourceUnavailableException(OracleDiscoveryScope.DOMAIN.name(), null, DiscoveryStep.DISCOVERY, "NO_INPUTS_DISCOVERED", message);
            }
            warnings.add(new DiscoveryWarning(OracleDiscoveryScope.DOMAIN.name(), null, DiscoveryStep.DISCOVERY, "NO_INPUTS_DISCOVERED", message));
        }
        return new DiscoveryResult(schemas, warnings, skipped, context.sourceMetadata());
    }
}

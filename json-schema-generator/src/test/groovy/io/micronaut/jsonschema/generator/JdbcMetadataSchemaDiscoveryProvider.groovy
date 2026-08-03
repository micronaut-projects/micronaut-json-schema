package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec

class JdbcMetadataSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    static final String ID = "custom-jdbc"

    @Override
    String providerId() {
        ID
    }

    @Override
    boolean usesJdbc() {
        true
    }

    @Override
    String sourceScope() {
        "CUSTOM_JDBC"
    }

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context,
                             SourceSpec source) throws Exception {
        return new DiscoveryResult(List.of(), List.of(), List.of())
    }
}

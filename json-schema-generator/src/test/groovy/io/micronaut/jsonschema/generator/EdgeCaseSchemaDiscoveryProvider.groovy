package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec

class EdgeCaseSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) throws Exception {
        return new DiscoveryResult([
            new DiscoveredSchema(
                "CUSTOM",
                source.option("schemaName") ?: "EDGE",
                source.option("schema"),
                "TEST"
            )
        ], List.of(), List.of())
    }
}

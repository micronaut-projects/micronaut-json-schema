package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.oracle.DiscoveredSchema
import io.micronaut.jsonschema.generator.oracle.DiscoveryResult
import io.micronaut.jsonschema.generator.oracle.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.oracle.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.oracle.SourceSpec

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

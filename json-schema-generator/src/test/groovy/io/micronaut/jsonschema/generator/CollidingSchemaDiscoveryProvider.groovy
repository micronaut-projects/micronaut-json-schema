package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec

class CollidingSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) throws Exception {
        return new DiscoveryResult([
            new DiscoveredSchema("CUSTOM", "CUSTOMER", '{"type":"object","additionalProperties":false}', "TEST"),
            new DiscoveredSchema("CUSTOM", "customer", '{"type":"object","additionalProperties":false}', "TEST")
        ], List.of(), List.of())
    }
}

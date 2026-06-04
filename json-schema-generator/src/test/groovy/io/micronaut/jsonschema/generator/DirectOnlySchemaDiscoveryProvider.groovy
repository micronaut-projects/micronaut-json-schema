package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec

class DirectOnlySchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    String providerId() {
        "direct-only"
    }

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) throws Exception {
        return new DiscoveryResult(List.of(), List.of(), List.of())
    }
}

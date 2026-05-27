package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.oracle.DiscoveryResult
import io.micronaut.jsonschema.generator.oracle.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.oracle.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.oracle.SourceSpec

class DirectOnlySchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) throws Exception {
        return new DiscoveryResult(List.of(), List.of(), List.of())
    }
}

package io.micronaut.jsonschema.generator.oracle

class TestSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context,
                             SourceSpec source) throws Exception {
        return new DiscoveryResult(List.of(), List.of(), List.of())
    }
}

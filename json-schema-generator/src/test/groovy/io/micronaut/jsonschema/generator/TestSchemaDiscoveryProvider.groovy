package io.micronaut.jsonschema.generator.oracle

class TestSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    @Override
    DiscoveryResult discover(SchemaDiscoveryContext context,
                             SourceSpec source) throws Exception {
        Map<String, String> metadata = [
            provider      : "test",
            schemaCacheDir: context.schemaCacheDir().toString(),
            outputDir     : context.outputDir().toString()
        ]
        if (source.option("apiToken") != null) {
            metadata.put("apiToken", source.option("apiToken"))
        }
        return new DiscoveryResult(List.of(), List.of(), List.of(), metadata)
    }
}

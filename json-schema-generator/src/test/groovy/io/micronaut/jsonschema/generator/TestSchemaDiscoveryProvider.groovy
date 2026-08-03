package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec

class TestSchemaDiscoveryProvider implements SchemaDiscoveryProvider {

    static final String ID = "test"

    @Override
    String providerId() {
        ID
    }

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

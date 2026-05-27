package io.micronaut.jsonschema.generator.oracle

import io.micronaut.jsonschema.generator.DirectOnlySchemaDiscoveryProvider
import spock.lang.Specification

class SchemaDiscoveryProvidersSpec extends Specification {

    void "resolver loads built in provider by class name"() {
        expect:
        SchemaDiscoveryProviders.resolve(
            OracleDomainSchemaDiscoveryProvider.name,
            OracleDomainSchemaDiscoveryProvider.classLoader
        ).class == OracleDomainSchemaDiscoveryProvider
    }

    void "resolver loads custom provider by class name without service registration"() {
        expect:
        SchemaDiscoveryProviders.resolve(
            DirectOnlySchemaDiscoveryProvider.name,
            DirectOnlySchemaDiscoveryProvider.classLoader
        ).class == DirectOnlySchemaDiscoveryProvider
    }
}

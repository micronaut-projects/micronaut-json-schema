package io.micronaut.jsonschema.generator.discovery

import io.micronaut.jsonschema.generator.DirectOnlySchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.TestSchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider
import spock.lang.Specification

class SchemaDiscoveryProvidersSpec extends Specification {

    void "resolver loads built in provider by id"() {
        expect:
        SchemaDiscoveryProviders.resolve(
            OracleDomainSchemaDiscoveryProvider.PROVIDER_ID,
            OracleDomainSchemaDiscoveryProvider.classLoader
        ).class == OracleDomainSchemaDiscoveryProvider
    }

    void "resolver loads custom provider by service registration"() {
        expect:
        SchemaDiscoveryProviders.resolve(
            TestSchemaDiscoveryProvider.ID,
            TestSchemaDiscoveryProvider.classLoader
        ).class == TestSchemaDiscoveryProvider
    }

    void "resolver does not load unregistered custom provider by class name"() {
        when:
        SchemaDiscoveryProviders.resolve(
            DirectOnlySchemaDiscoveryProvider.name,
            DirectOnlySchemaDiscoveryProvider.classLoader
        )

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("Unable to locate schema discovery provider")
    }
}

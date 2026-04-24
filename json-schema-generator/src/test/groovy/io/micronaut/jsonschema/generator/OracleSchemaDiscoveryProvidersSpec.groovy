package io.micronaut.jsonschema.generator.oracle

import spock.lang.Specification

class OracleSchemaDiscoveryProvidersSpec extends Specification {

    void "resolver loads built in provider from service loader"() {
        expect:
        OracleSchemaDiscoveryProviders.resolve(
            OracleDomainDiscoveryProvider.name,
            OracleDomainDiscoveryProvider.classLoader
        ).class == OracleDomainDiscoveryProvider
    }

    void "resolver loads custom provider from service loader"() {
        expect:
        OracleSchemaDiscoveryProviders.resolve(
            TestOracleSchemaDiscoveryProvider.name,
            TestOracleSchemaDiscoveryProvider.classLoader
        ).class == TestOracleSchemaDiscoveryProvider
    }
}

package io.micronaut.jsonschema.generator.oracle

import java.sql.Connection

class TestOracleSchemaDiscoveryProvider implements OracleSchemaDiscoveryProvider {

    @Override
    OracleDiscoveryResult discover(Connection connection,
                                   OracleSourceSpec source,
                                   boolean skipOnError,
                                   OracleJsonSchemaLogger logger) throws Exception {
        return new OracleDiscoveryResult(List.of(), List.of(), List.of())
    }
}

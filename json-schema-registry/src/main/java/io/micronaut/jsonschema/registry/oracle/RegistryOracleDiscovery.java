/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.jsonschema.generator.discovery.DiscoveryResult;
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared bridge for invoking generator discovery providers from registry materializers.
 *
 * @since 2.2.0
 */
final class RegistryOracleDiscovery {

    private RegistryOracleDiscovery() {
    }

    static DiscoveryResult discover(SchemaDiscoveryProvider provider,
                                    Connection connection,
                                    String sourceName,
                                    String owner,
                                    Map<String, String> options,
                                    JsonSchemaRecordsLogger logger) throws Exception {
        Map<String, Object> sourceOptions = new LinkedHashMap<>(options);
        if (owner != null && !owner.isBlank()) {
            sourceOptions.put("owner", owner);
        }
        SchemaDiscoveryContext context = new SchemaDiscoveryContext(
            true,
            false,
            null,
            null,
            Map.of(),
            logger,
            () -> connection
        );
        return provider.discover(context, new SourceSpec(sourceName, provider.providerId(), sourceOptions));
    }
}

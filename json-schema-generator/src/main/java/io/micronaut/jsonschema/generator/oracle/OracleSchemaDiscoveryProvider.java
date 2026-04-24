/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.generator.oracle;

import java.sql.Connection;

/**
 * SPI for retrieving JSON Schema documents from Oracle metadata.
 * Implementations must be registered through {@link java.util.ServiceLoader}
 * in {@code META-INF/services/io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider}.
 *
 * @since 2.0.0
 */
public interface OracleSchemaDiscoveryProvider {

    /**
     * Discover schemas for the supplied source specification.
     *
     * @param connection The open JDBC connection
     * @param source The source specification
     * @param skipOnError Whether per-object failures should be skipped
     * @param logger The logger for diagnostics
     * @return The discovery result
     * @throws Exception If discovery fails
     */
    OracleDiscoveryResult discover(Connection connection,
                                   OracleSourceSpec source,
                                   boolean skipOnError,
                                   OracleJsonSchemaLogger logger) throws Exception;
}

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
package io.micronaut.jsonschema.generator.discovery;

/**
 * SPI for retrieving JSON Schema documents from pluggable sources.
 * Implementations are selected by stable provider id. Built-in providers are
 * registered directly; custom providers are loaded through {@link java.util.ServiceLoader}.
 *
 * @since 2.0.0
 */
public interface SchemaDiscoveryProvider {

    /**
     * @return Stable provider id used in source configuration
     */
    String providerId();

    /**
     * @return {@code true} when this provider uses the shared JDBC connection configured
     *         for the record-generation pipeline
     */
    default boolean usesJdbc() {
        return false;
    }

    /**
     * @return Source-level diagnostic scope used when discovery fails before object-level
     *         diagnostics can be emitted
     */
    default String sourceScope() {
        return "SOURCE";
    }

    /**
     * Discover schemas for the supplied source specification.
     *
     * @param context The discovery context
     * @param source The source specification
     * @return The discovery result
     * @throws Exception If discovery fails
     */
    DiscoveryResult discover(SchemaDiscoveryContext context,
                             SourceSpec source) throws Exception;
}

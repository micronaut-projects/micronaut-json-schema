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

/**
 * SPI for retrieving JSON Schema documents from pluggable sources.
 * Implementations are selected by the configured provider class name and must
 * expose an accessible no-argument constructor.
 *
 * @since 2.0.0
 */
public interface SchemaDiscoveryProvider {

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

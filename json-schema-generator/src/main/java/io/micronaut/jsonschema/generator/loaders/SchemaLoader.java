/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jsonschema.generator.loaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;

/**
 * Interface for loading a JSON schema from various sources.
 *
 * @author Elif Kurtay
 * @version 1.3
 */
public interface SchemaLoader {
    ObjectMapper JSON_MAPPER = JsonSchemaMapperFactory.createMapper();

    /**
     * Loads a JSON schema.
     *
     * @return a {@link Schema} object representing the loaded JSON schema
     * @throws RuntimeException if no valid source is found or if there is an error loading the schema
     */
    Schema load();
}

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
package io.micronaut.jsonschema.registry;

import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Jackson-backed schema normalizer.
 *
 * @since 2.0.0
 */
@Singleton
public final class DefaultJsonSchemaNormalizer implements JsonSchemaNormalizer {
    private final ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper();

    @Override
    public String normalize(String jsonSchema) throws IOException {
        Object value = objectMapper.readValue(jsonSchema, Object.class);
        return objectMapper.writeValueAsString(value);
    }
}

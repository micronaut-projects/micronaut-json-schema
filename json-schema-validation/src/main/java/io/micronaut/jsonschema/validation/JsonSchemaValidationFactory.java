/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.jsonschema.validation;

import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.path.PathType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;

/**
 * {@link Factory} to instantiate {@link SchemaRegistryConfig} beans related to JSON Schema validation.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Internal
@Factory
class JsonSchemaValidationFactory {
    @Bean
    SchemaRegistryConfig schemaRegistryConfig() {
        return SchemaRegistryConfig.builder()
            .pathType(PathType.JSON_POINTER)
            .formatAssertionsEnabled(true)
            .build();
    }
}

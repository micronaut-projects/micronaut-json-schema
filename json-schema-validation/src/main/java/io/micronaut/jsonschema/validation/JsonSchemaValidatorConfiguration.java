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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * A configuration for {@link JsonSchemaValidator}.
 * The validator will resolve URIs that start with base URI to JSON schemas in the specified
 * folder on the classpath.
 *
 * @param baseUri The base URI for JSON schemas to be validated
 * @param classpathFolder THe folder where the JSON schemas are located, on the classpath
 *
 * @author Andriy Dmytruk
 * @since 1.0.0
 */
@ConfigurationProperties(JsonSchemaValidatorConfiguration.PREFIX)
public record JsonSchemaValidatorConfiguration(
    @Bindable(defaultValue = "http://localhost:8080/schemas/")
    String baseUri,
    @Bindable(defaultValue = "META-INF/schemas/")
    String classpathFolder
) {

    public static final String PREFIX = "micronaut.jsonschema.validation";

}

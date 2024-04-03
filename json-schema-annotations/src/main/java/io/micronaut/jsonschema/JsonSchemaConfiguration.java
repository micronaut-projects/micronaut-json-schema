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
package io.micronaut.jsonschema;

/**
 * An annotation for globally configuring the JSON schema generation.
 *
 * @since 1.0.0
 * @author Andriy Dmytruk
 */
public @interface JsonSchemaConfiguration {

    /**
     * The location where JSON schemas will be generated inside the build {@code META-INF/} directory.
     *
     * @return The output location
     */
    String outputLocation() default "schemas";

    /**
     * The base URI to be used for schemas.
     *
     * @return The base URI
     */
    String baseUri();

    /**
     * Whether to encode byte array as a JSON array.
     * The default and preferred behavior is to encode it as a Base64 string.
     *
     * @return Whether to represent binary data as array
     */
    boolean binaryAsArray() default false;

}

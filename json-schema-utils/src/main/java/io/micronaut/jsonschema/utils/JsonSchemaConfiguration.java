/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.utils;

/**
 * Json Schema Configuration.
 */
public interface JsonSchemaConfiguration {
    String DEFAULT_OUTPUT_LOCATION = "schemas";

    /**
     * The location where JSON schemas will be generated inside the build META-INF/ directory. It defaults to {@value DEFAULT_OUTPUT_LOCATION}.
     * @return The location where JSON schemas will be generated inside the build META-INF/ directory. It defaults to {@value DEFAULT_OUTPUT_LOCATION}
     */
    default String getOutputLocation() {
        return DEFAULT_OUTPUT_LOCATION;
    }
}

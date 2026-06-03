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

import io.micronaut.core.annotation.Nullable;

import java.util.Map;

/**
 * Oracle discovery source configuration.
 *
 * @param name Provider configuration name
 * @param providerClassName Provider implementation class name
 * @param owner Optional Oracle owner/schema
 * @param options Provider options
 * @since 2.0.0
 */
public record OracleSourceSpec(
    String name,
    String providerClassName,
    @Nullable String owner,
    Map<String, String> options
) {

    /**
     * Create an immutable source spec.
     *
     * @param name Provider configuration name
     * @param providerClassName Provider implementation class name
     * @param owner Optional Oracle owner/schema
     * @param options Provider options
     */
    public OracleSourceSpec {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}

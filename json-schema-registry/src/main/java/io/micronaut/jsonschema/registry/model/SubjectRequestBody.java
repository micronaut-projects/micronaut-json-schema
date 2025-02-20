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
package io.micronaut.jsonschema.registry.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a request body for a subject in the Schema Registry.
 *
 * @param schema The schema.
 * @param schemaType The schema type.
 * @param references The references.
 * @param metadata The metadata.
 * @param ruleSet The rule set.
 */
@Serdeable
@Introspected
public record SubjectRequestBody(
    String schema,
    SchemaType schemaType,
    List<ReferenceType> references,
    Map<String, String> metadata,
    Set<String> ruleSet
) {
    /**
     * The reference type used in the schema registry.
     *
     * @param name The reference name.
     * @param subject The subject name.
     * @param version The subject version.
     */
    @Introspected
    public record ReferenceType(
        String name,
        String subject,
        String version) {
    }
}

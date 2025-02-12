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
package io.micronaut.jsonschema.registry.types;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response types for the JSON Schema Registry Client.
 */
@Serdeable
public interface Responses {
    /**
     * The Schema Types available on the registry.
     */
    enum SchemaType {
        AVRO,
        JSON,
        PROTOBUF
    }

    /**
     * The ID of a schema.
     *
     * @param id The ID
     */
    @Introspected
    record Id(
        int id
    ) {
    }

    /**
     * The JSON Schema in string.
     *
     * @param schema The schema string
     */
    @Introspected
    record SchemaJson(
        String schema
    ) {
    }

    /**
     * The (subject,version) pairs available at the registry.
     *
     * @param subject The subject name
     * @param version The version of the subject
     */
    @Introspected
    record SubjectVersion(
        String subject,
        int version
    ) {
    }

    /**
     * The subject details available at the registry.
     *
     * @param subject The subject name
     * @param id The unique global ID of the schema
     * @param version The version of the schema
     * @param schemaType The type of the schema
     * @param schema The schema string
     */
    @Introspected
    record Subject(
        String subject,
        int id,
        int version,
        SchemaType schemaType,
        String schema
    ) {
    }

    /**
     * The mode of the registry.
     *
     * @param mode The mode type
     */
    @Introspected
    record Mode(
        ModeType mode
    ) {
        /**
         * The mode type.
         */
        public enum ModeType {
            IMPORT,
            READONLY,
            READWRITE
        }
    }

    /**
     * The compatibility of the schema.
     *
     * @param is_compatible Whether the two schemas are compatible
     */
    @Introspected
    record Compatibility(
        boolean is_compatible
    ) {
    }

    /**
     * The compatibility level.
     */
    enum CompatibilityLevel {
        NONE,
        BACKWARD,
        BACKWARD_TRANSITIVE,
        FORWARD,
        FORWARD_TRANSITIVE,
        FULL,
        FULL_TRANSITIVE
    }

    /**
     * The configuration of the registry.
     *
     * @param alias The alias of the registry
     * @param normalize Whether to normalize the schema
     * @param compatibility The compatibility level
     * @param compatibilityGroup The compatibility group
     *                           (used for compatibility checks)
     * @param defaultMetadata The default metadata
     * @param overrideMetadata The override metadata
     * @param defaultRuleSet The default rule set
     * @param overrideRuleSet The override rule set
     */
    @Introspected
    record Config(
        String alias,
        boolean normalize,
        @JsonAlias("compatibilityLevel") CompatibilityLevel compatibility,
        String compatibilityGroup,
        Object defaultMetadata,
        Object overrideMetadata,
        Object defaultRuleSet,
        Object overrideRuleSet
    ) {
    }
}

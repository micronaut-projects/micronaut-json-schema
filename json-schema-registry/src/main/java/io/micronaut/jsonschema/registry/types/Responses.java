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

@Serdeable
public interface Responses {
    enum SchemaType {
        AVRO,
        JSON,
        PROTOBUF
    }

    record Id(
        int id
    ) {
    }

    record Schema(
        String schema
    ) {
    }

    record SubjectVersion(
        String subject,
        int version
    ) {
    }

    @Introspected
    record Subject(
        String subject,
        int id,
        int version,
        SchemaType schemaType,
        String schema
    ) {
    }

    record Mode(
        ModeType mode
    ) {
        enum ModeType {
            IMPORT,
            READONLY,
            READWRITE
        }
    }

    record Compatibility(
        boolean is_compatible
    ) {
    }

    enum CompatibilityLevel {
        NONE,
        BACKWARD,
        BACKWARD_TRANSITIVE,
        FORWARD,
        FORWARD_TRANSITIVE,
        FULL,
        FULL_TRANSITIVE
    }

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

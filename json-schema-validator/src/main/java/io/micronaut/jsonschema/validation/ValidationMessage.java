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

import org.jspecify.annotations.Nullable;

/**
 * JSON Schema Validation Message.
 * @author Sergio del Amo
 * @since 1.0.0
 */
@FunctionalInterface
public interface ValidationMessage {

    /**
     * Returns the validation message.
     *
     * @return JSON Schema Validation Message, prefixed with the JSON Pointer of the invalid instance location
     */
    String getMessage();

    /**
     * Returns the location of the invalid value.
     *
     * @return The JSON Pointer (RFC 6901) of the instance location that failed validation, empty for the root
     * @since 2.3.2
     */
    default String getInstanceLocation() {
        return "";
    }

    /**
     * Returns the keyword that failed.
     *
     * @return The JSON Schema keyword that failed validation, if known
     * @since 2.3.2
     */
    default @Nullable String getKeyword() {
        return null;
    }

    /**
     * Returns the location of the failing keyword in the schema.
     *
     * @return The absolute location of the failing keyword within the schema (base URI plus JSON Pointer fragment), if known
     * @since 2.3.2
     */
    default @Nullable String getSchemaLocation() {
        return null;
    }
}

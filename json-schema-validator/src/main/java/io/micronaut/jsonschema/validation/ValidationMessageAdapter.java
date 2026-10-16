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

import io.micronaut.core.annotation.Internal;

/**
 * Default {@link ValidationMessage} implementation produced by {@link JsonSchemaValidator}.
 * Prior to 2.3.2 this type adapted a NetworkNT {@code com.networknt.schema.Error}; validation is now
 * implemented by Micronaut JSON Schema itself and this type carries the error details directly.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Internal
public class ValidationMessageAdapter implements ValidationMessage {
    private final String instanceLocation;
    private final String keyword;
    private final String schemaLocation;
    private final String detail;

    /**
     * Creates a validation message.
     *
     * @param instanceLocation The JSON Pointer of the invalid instance location
     * @param keyword The keyword that failed
     * @param schemaLocation The absolute location of the keyword in the schema
     * @param detail The message, without the instance location
     * @since 2.3.2
     */
    public ValidationMessageAdapter(String instanceLocation, String keyword, String schemaLocation, String detail) {
        this.instanceLocation = instanceLocation;
        this.keyword = keyword;
        this.schemaLocation = schemaLocation;
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        return instanceLocation + ": " + detail;
    }

    @Override
    public String getInstanceLocation() {
        return instanceLocation;
    }

    @Override
    public String getKeyword() {
        return keyword;
    }

    @Override
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Returns the message without the instance location.
     *
     * @return The message without the instance location prefix
     * @since 2.3.2
     */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "ValidationMessageAdapter{message=" + getMessage() + "}";
    }
}

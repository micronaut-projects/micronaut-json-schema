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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaType;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Internal
final class SchemaTypes {
    private SchemaTypes() {
    }

    @Nullable
    static ConfigurationSchemaType toType(@Nullable Object typeValue) {
        if (typeValue == null) {
            return null;
        }
        if (typeValue instanceof String s) {
            return ConfigurationSchemaType.of(s);
        }
        if (typeValue instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String s) {
            // Micronaut schema model commonly uses a single type.
            return ConfigurationSchemaType.of(s);
        }
        return null;
    }
}

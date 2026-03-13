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
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchema;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;

@Internal
final class ConfigurationSchemaPropertyAdapter {
    private ConfigurationSchemaPropertyAdapter() {
    }

    static ConfigurationSchemaProperty fromRoot(ConfigurationSchema schema) {
        return new ConfigurationSchemaProperty(
            schema.type(),
            schema.description(),
            schema.format(),
            null,
            schema.pattern(),
            schema.minLength(),
            schema.maxLength(),
            schema.minItems(),
            schema.maxItems(),
            schema.uniqueItems(),
            schema.multipleOf(),
            schema.constValue(),
            schema.defaultValue(),
            null,
            null,
            null,
            schema.properties(),
            schema.required(),
            schema.minProperties(),
            schema.maxProperties(),
            schema.enumValues(),
            schema.minimum(),
            schema.exclusiveMinimum(),
            schema.maximum(),
            schema.exclusiveMaximum(),
            schema.items(),
            schema.additionalProperties(),
            schema.defs(),
            schema.ref()
        );
    }
}

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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyEntry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchema;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchemaProperty;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Internal
final class SchemaContext {
    private static final Argument<JsonSchemaProperty> JSON_SCHEMA_PROPERTY_ARGUMENT = Argument.of(JsonSchemaProperty.class);

    private final JsonSchema root;
    private final ClassLoader classLoader;
    private final Environment environment;
    private final JsonMapper jsonMapper;
    private final boolean failOnNotPresent;
    private final RefResolver refResolver;

    SchemaContext(
        JsonSchema root,
        ClassLoader classLoader,
        Environment environment,
        JsonMapper jsonMapper,
        boolean failOnNotPresent
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.failOnNotPresent = failOnNotPresent;
        this.refResolver = new RefResolver();
    }

    JsonSchema root() {
        return root;
    }

    ClassLoader classLoader() {
        return classLoader;
    }

    Environment environment() {
        return environment;
    }

    JsonMapper jsonMapper() {
        return jsonMapper;
    }

    boolean failOnNotPresent() {
        return failOnNotPresent;
    }

    RefResolver refResolver() {
        return refResolver;
    }

    ConfigurationError error(String property, String message) {
        Optional<PropertyEntry> entry = environment.getPropertyEntry(property);
        if (entry.isPresent()) {
            PropertyEntry propertyEntry = entry.get();
            return new ConfigurationError(
                property,
                message,
                propertyEntry.origin() != null ? propertyEntry.origin().location() : null,
                propertyEntry.raw(),
                propertyEntry.value()
            );
        }
        return new ConfigurationError(property, message, null, null, null);
    }

    String resolvedPropertyName(String computedPropertyName, @Nullable String micronautPath, @Nullable String wildcardReplacement) {
        if (micronautPath != null) {
            if (wildcardReplacement != null) {
                return micronautPath.replace("*", wildcardReplacement);
            }
            return micronautPath;
        }
        return computedPropertyName;
    }

    @Internal
    final class RefResolver {
        @Nullable
        JsonSchemaProperty resolveAdditionalPropertiesSchema(JsonSchemaProperty schema) {
            Object additionalProperties = schema.additionalProperties();
            if (additionalProperties == null) {
                return null;
            }
            if (additionalProperties instanceof Boolean) {
                return null;
            }
            if (additionalProperties instanceof JsonSchemaProperty prop) {
                return resolveRef(prop);
            }
            if (additionalProperties instanceof Map) {
                JsonSchemaProperty parsed = parseProperty(additionalProperties);
                return parsed != null ? resolveRef(parsed) : null;
            }
            return null;
        }

        @Nullable
        JsonSchemaProperty resolveRef(JsonSchemaProperty schema) {
            if (schema.ref() == null) {
                return schema;
            }
            return resolveRef(schema.ref());
        }

        @Nullable
        JsonSchemaProperty resolveRef(String ref) {
            if (!ref.startsWith("#/")) {
                return null;
            }
            List<String> tokens = JsonPointer.parse(ref);
            Object current = root;
            for (String token : tokens) {
                if (current instanceof JsonSchema jsonSchema) {
                    current = resolveFromRoot(jsonSchema, token);
                } else if (current instanceof JsonSchemaProperty property) {
                    current = resolveFromProperty(property, token);
                } else if (current instanceof Map<?, ?> map) {
                    current = map.get(token);
                } else {
                    return null;
                }
                if (current == null) {
                    return null;
                }
            }
            if (current instanceof JsonSchemaProperty prop) {
                return prop.ref() != null ? resolveRef(prop.ref()) : prop;
            }
            return null;
        }

        private static Object resolveFromRoot(JsonSchema schema, String token) {
            if ("$defs".equals(token)) {
                return schema.defs();
            }
            if ("properties".equals(token)) {
                return schema.properties();
            }
            return null;
        }

        private static Object resolveFromProperty(JsonSchemaProperty schema, String token) {
            if ("$defs".equals(token)) {
                return schema.defs();
            }
            if ("properties".equals(token)) {
                return schema.properties();
            }
            return null;
        }

        @Nullable
        private JsonSchemaProperty parseProperty(Object mapLike) {
            try {
                String json = jsonMapper.writeValueAsString(mapLike);
                return jsonMapper.readValue(json, JSON_SCHEMA_PROPERTY_ARGUMENT);
            } catch (IOException e) {
                return null;
            }
        }
    }
}

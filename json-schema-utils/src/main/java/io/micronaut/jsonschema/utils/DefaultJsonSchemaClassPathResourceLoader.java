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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.Readable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.jsonschema.JsonSchema;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.jsonschema.utils.JsonSchemaResourceUtils.CLASSPATH_PREFIX;

/**
 * @since 1.7.0
 */
@Singleton
@Internal
class DefaultJsonSchemaClassPathResourceLoader implements JsonSchemaClassPathResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaClassPathResourceLoader.class);
    private static final String SUFFIX = ".schema.json";
    private static final String MEMBER_EMBEDDED = "embedded";
    private static final String MEMBER_URI = "uri";
    private static final String META_INF = "META-INF";
    private static final String SLASH = "/";
    private final ResourceLoader resourceLoader;
    private final JsonSchemaConfiguration jsonSchemaConfiguration;

    DefaultJsonSchemaClassPathResourceLoader(ResourceLoader resourceLoader,
                                             JsonSchemaConfiguration jsonSchemaConfiguration) {
        this.resourceLoader = resourceLoader;
        this.jsonSchemaConfiguration = jsonSchemaConfiguration;
    }

    public <T> Optional<String> jsonSchemaStringForClass(@NonNull Class<T> type) {
        String path = jsonSchemaPath(type);
        Optional<InputStream> resourceAsStream = resourceLoader.getResourceAsStream(path);
        if (resourceAsStream.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No schema found for type: {} at path: {}", type, path);
            }
            return embeddedJsonSchema(type);
        }
        try (InputStream inputStream = resourceAsStream.get()) {
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error loading schema", e);
            }
            return embeddedJsonSchema(type);
        }
    }

    @Override
    @NonNull
    public Map<String, Readable> jsonSchemas() {
        ClassLoader classLoader = resolveClassLoader();
        if (classLoader == null) {
            return Map.of();
        }

        String schemaFolder = META_INF + SLASH + jsonSchemaConfiguration.getOutputLocation() + SLASH;
        return JsonSchemaResourceUtils.resolveSchemas(resourceLoader , classLoader, schemaFolder);
    }

    @Nullable
    private ClassLoader resolveClassLoader() {
        if (resourceLoader instanceof ClassPathResourceLoader classPathResourceLoader) {
            return classPathResourceLoader.getClassLoader();
        }
        if (resourceLoader instanceof Environment environment) {
            return environment.getClassLoader();
        }
        return null;
    }

    private <T> String jsonSchemaPath(@NonNull Class<T> type) {
        String className = NameUtils.hyphenate(type.getSimpleName());
        try {
            BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(type);
            AnnotationValue<JsonSchema> jsonSchemaAnnotationValue = introspection.getAnnotation(JsonSchema.class);
            if (jsonSchemaAnnotationValue == null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("JsonSchema annotation not found for type: {}, falling back to conventional schema path", type);
                }
            } else {
                Optional<String> uriOptional = jsonSchemaAnnotationValue.stringValue(MEMBER_URI);
                if (uriOptional.isPresent()) {
                    className = uriOptional.get().replace(SLASH, "");
                }
            }
        } catch (IntrospectionException e) {
            LOG.debug("Introspection exception for class {}.}", type, e);
        }
        String name = className + SUFFIX;
        return CLASSPATH_PREFIX + String.join(SLASH, META_INF, jsonSchemaConfiguration.getOutputLocation(), name);
    }

    private <T> Optional<String> embeddedJsonSchema(@NonNull Class<T> type) {
        try {
            BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(type);
            AnnotationValue<JsonSchema> jsonSchemaAnnotationValue = introspection.getAnnotation(JsonSchema.class);
            if (jsonSchemaAnnotationValue != null) {
                String[] embedded = jsonSchemaAnnotationValue.stringValues(MEMBER_EMBEDDED);
                if (embedded.length > 0) {
                    return Optional.of(String.join("", embedded));
                }
            }
        } catch (IntrospectionException e) {
            LOG.debug("Introspection exception for class {} while reading embedded schema.", type, e);
        }

        JsonSchema jsonSchema = type.getAnnotation(JsonSchema.class);
        if (jsonSchema != null && jsonSchema.embedded().length > 0) {
            return Optional.of(String.join("", jsonSchema.embedded()));
        }
        return Optional.empty();
    }
}

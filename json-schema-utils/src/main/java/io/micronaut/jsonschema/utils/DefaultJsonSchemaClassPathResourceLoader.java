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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.jsonschema.JsonSchema;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * @since 1.7.0
 */
@Singleton
@Internal
class DefaultJsonSchemaClassPathResourceLoader implements JsonSchemaClassPathResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaClassPathResourceLoader.class);
    private static final String SUFFIX = ".schema.json";
    private static final String MEMBER_URI = "uri";
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String META_INF = "META-INF";
    private static final String SLASH = "/";
    private final ResourceLoader resourceLoader;
    private final JsonSchemaConfiguration jsonSchemaConfiguration;

    DefaultJsonSchemaClassPathResourceLoader(ResourceLoader resourceLoader,
                                             JsonSchemaConfiguration jsonSchemaConfiguration) {
        this.resourceLoader = resourceLoader;
        this.jsonSchemaConfiguration = jsonSchemaConfiguration;
    }

    @Nullable
    public <T> Optional<String> jsonSchemaStringForClass(@NonNull Class<T> type) {

        Optional<String> pathOptional = jsonSchemaPath(type);
        if (pathOptional.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No schema path found for type: {}", type);
            }
            return Optional.empty();
        }
        String path = pathOptional.get();
        Optional<InputStream> resourceAsStream = resourceLoader.getResourceAsStream(path);
        if (resourceAsStream.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No schema found for type: {} at path: {}", type, path);
            }
            return Optional.empty();
        }
        try (InputStream inputStream = resourceAsStream.get()) {
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error loading schema", e);
            }
            return Optional.empty();
        }
    }

    private <T> Optional<String> jsonSchemaPath(@NonNull Class<T> type) {
        String className = NameUtils.hyphenate(type.getSimpleName());
        try {
            BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(type);
            AnnotationValue<JsonSchema> jsonSchemaAnnotationValue = introspection.getAnnotation(io.micronaut.jsonschema.JsonSchema.class);
            if (jsonSchemaAnnotationValue == null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("JsonSchema annotation not found for type: {}", type);
                }
                return Optional.empty();
            }
            Optional<String> uriOptional = jsonSchemaAnnotationValue.stringValue(MEMBER_URI);
            if (uriOptional.isPresent()) {
                className = uriOptional.get().replace(SLASH, "");
            }
        } catch (IntrospectionException e) {
            LOG.debug("Introspection exception for class {}.}", type, e);
        }
        String name = className + SUFFIX;
        return Optional.of(CLASSPATH_PREFIX + String.join(SLASH, META_INF, jsonSchemaConfiguration.getOutputLocation(), name));
    }
}

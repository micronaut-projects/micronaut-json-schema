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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.io.Readable;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A composite {@link JsonSchemaClassPathResourceLoader} that delegates to other registered loaders
 * and returns the first non-empty result.
 *
 * @since 2.0.0
 */
@Singleton
@Primary
@Internal
final class CompositeJsonSchemaClassPathResourceLoader implements JsonSchemaClassPathResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(CompositeJsonSchemaClassPathResourceLoader.class);

    private final BeanProvider<@NonNull JsonSchemaClassPathResourceLoader> loaders;

    CompositeJsonSchemaClassPathResourceLoader(BeanProvider<@NonNull JsonSchemaClassPathResourceLoader> loaders) {
        this.loaders = loaders;
    }

    @Override
    public <T> Optional<String> jsonSchemaStringForClass(@NonNull Class<T> type) {
        for (JsonSchemaClassPathResourceLoader loader : loaders) {
            // Avoid infinite recursion by skipping this composite instance
            if (loader == this) {
                continue;
            }
            try {
                Optional<String> result = loader.jsonSchemaStringForClass(type);
                if (result.isPresent()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Schema for {} resolved by {}", type, loader.getClass().getSimpleName());
                    }
                    return result;
                }
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Loader {} failed to resolve schema for {}", loader.getClass().getName(), type, e);
                }
            }
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("No schema found for type: {} via composite", type);
        }
        return Optional.empty();
    }

    @Override
    @NonNull
    public Map<String, Readable> jsonSchemas() {
        Map<String, Readable> schemas = new LinkedHashMap<>();
        for (JsonSchemaClassPathResourceLoader loader : loaders) {
            if (loader == this) {
                continue;
            }
            try {
                Map<String, Readable> loaderSchemas = loader.jsonSchemas();
                if (!loaderSchemas.isEmpty()) {
                    loaderSchemas.forEach(schemas::putIfAbsent);
                }
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Loader {} failed to resolve schemas", loader.getClass().getName(), e);
                }
            }
        }
        return schemas;
    }
}

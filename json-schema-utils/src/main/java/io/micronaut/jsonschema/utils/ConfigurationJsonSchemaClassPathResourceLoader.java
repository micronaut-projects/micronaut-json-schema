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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Loads JSON Schemas for Micronaut {@code @ConfigurationProperties} from classpath resources.
 *
 * Micronaut Core 5.0.0-M10+ ships JSON schemas for configuration properties under
 * {@code META-INF/micronaut-configuration-schemas}.
 *
 * @since 2.0.0
 */
@Singleton
@Internal
final class ConfigurationJsonSchemaClassPathResourceLoader implements JsonSchemaClassPathResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationJsonSchemaClassPathResourceLoader.class);

    private static final String SUFFIX = ".json";

    private final ResourceLoader resourceLoader;

    ConfigurationJsonSchemaClassPathResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public <T> Optional<String> jsonSchemaStringForClass(@NonNull Class<T> type) {
        String path = jsonSchemaPath(type);
        Optional<InputStream> resourceAsStream = resourceLoader.getResourceAsStream(path);
        if (resourceAsStream.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No configuration schema found for type: {} at path: {}", type, path);
            }
            return Optional.empty();
        }
        try (InputStream inputStream = resourceAsStream.get()) {
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error loading configuration schema for {}", type, e);
            }
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public Map<String, Readable> jsonSchemas() {
        ClassLoader classLoader = resolveClassLoader();
        if (classLoader == null) {
            return Map.of();
        }

        String schemaFolder = JsonSchemaResourceUtils.configurationSchemasFolder();
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

    private static <T> String jsonSchemaPath(@NonNull Class<T> type) {
        String name = type.getName() + SUFFIX;
        return JsonSchemaResourceUtils.CLASSPATH_PREFIX + JsonSchemaResourceUtils.configurationSchemasFolder() + name;
    }
}

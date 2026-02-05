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
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String META_INF = "META-INF";
    private static final String CONFIGURATION_SCHEMAS = "micronaut-configuration-schemas";
    private static final String SLASH = "/";
    private static final String SUFFIX = ".json";

    private final ResourceLoader resourceLoader;

    ConfigurationJsonSchemaClassPathResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    @Nullable
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

    private static <T> String jsonSchemaPath(@NonNull Class<T> type) {
        String name = type.getName() + SUFFIX;
        return CLASSPATH_PREFIX + String.join(SLASH, META_INF, CONFIGURATION_SCHEMAS, name);
    }
}

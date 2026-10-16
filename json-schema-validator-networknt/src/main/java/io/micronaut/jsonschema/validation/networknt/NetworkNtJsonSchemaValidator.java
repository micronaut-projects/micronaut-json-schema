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
package io.micronaut.jsonschema.validation.networknt;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.ResourceLoader;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.jsonschema.utils.JsonSchemaConfiguration;
import io.micronaut.jsonschema.utils.JsonSchemaResourceUtils;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.JsonSchemaValidatorConfiguration;
import io.micronaut.jsonschema.validation.ValidationMessage;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link JsonSchemaValidator} implemented with the NetworkNT JSON Schema Validator. It replaces the built-in
 * validator when this module is on the classpath.
 *
 * @author Sergio del Amo
 * @since 2.3.2
 */
@Singleton
@Replaces(JsonSchemaValidator.class)
@Internal
final class NetworkNtJsonSchemaValidator implements JsonSchemaValidator {
    private final Map<Class<?>, Schema> jsonSchemaCache = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final SchemaRegistry schemaRegistry;
    private final JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader;

    NetworkNtJsonSchemaValidator(
        JsonSchemaValidatorConfiguration config,
        io.micronaut.core.io.ResourceLoader resourceLoader,
        JsonMapper jsonMapper,
        SchemaRegistryConfig schemaRegistryConfig,
        JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader,
        JsonSchemaConfiguration jsonSchemaConfiguration
    ) {
        this.jsonMapper = jsonMapper;
        this.jsonSchemaClassPathResourceLoader = jsonSchemaClassPathResourceLoader;
        this.schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft202012(), builder -> builder
            .schemaRegistryConfig(schemaRegistryConfig)
            .resourceLoaders(resourceLoaders -> resourceLoaders.add(new ClasspathSchemaResourceLoader(
                jsonSchemaConfiguration,
                config.baseUri(),
                config.classpathFolder(),
                resourceLoader
            ))));
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(String json, Class<T> type) {
        Schema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, json);
    }

    @Override
    public Set<? extends ValidationMessage> validate(Object value, Map<String, Object> jsonSchema) throws IOException {
        Schema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    public Set<? extends ValidationMessage> validate(Object value, String jsonSchema) throws IOException {
        Schema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(Object value, Class<T> type) throws IOException {
        Schema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, value);
    }

    private <T> Schema jsonSchema(Class<T> type) {
        String jsonSchema = jsonSchemaClassPathResourceLoader.jsonSchemaStringForClass(type).orElse(null);
        if (jsonSchema == null) {
            throw new IllegalArgumentException("No schema found for type: " + type);
        }
        return jsonSchema(jsonSchema);
    }

    private Schema jsonSchema(Map<String, Object> jsonSchema) {
        try {
            return jsonSchema(jsonMapper.writeValueAsString(jsonSchema));
        } catch (IOException e) {
            throw new IllegalArgumentException("could not serialize JSON Schema from: " + jsonSchema, e);
        }
    }

    private Schema jsonSchema(String jsonSchema) {
        return schemaRegistry.getSchema(jsonSchema, InputFormat.JSON);
    }

    private Set<? extends ValidationMessage> validate(Schema schema, Object value) throws IOException {
        String json = value instanceof String s ? s : jsonMapper.writeValueAsString(value);
        return validate(schema, json);
    }

    private static Set<? extends ValidationMessage> validate(Schema schema, String json) {
        return adapt(schema.validate(json, InputFormat.JSON));
    }

    private static Set<ValidationMessage> adapt(List<Error> errors) {
        Set<ValidationMessage> messages = new LinkedHashSet<>(errors.size());
        for (Error error : errors) {
            messages.add(new NetworkNtValidationMessage(error));
        }
        return messages;
    }

    /**
     * Resolves schema URIs to JSON schema files on the classpath.
     */
    private static final class ClasspathSchemaResourceLoader implements ResourceLoader {
        private final JsonSchemaConfiguration jsonSchemaConfiguration;
        private final @Nullable String baseUri;
        private final String fallbackClasspathFolder;
        private final io.micronaut.core.io.ResourceLoader resourceLoader;

        private ClasspathSchemaResourceLoader(JsonSchemaConfiguration jsonSchemaConfiguration,
                                              @Nullable String baseUri,
                                              String fallbackClasspathFolder,
                                              io.micronaut.core.io.ResourceLoader resourceLoader) {
            this.jsonSchemaConfiguration = jsonSchemaConfiguration;
            this.baseUri = baseUri;
            this.fallbackClasspathFolder = fallbackClasspathFolder;
            this.resourceLoader = resourceLoader;
        }

        @Override
        public InputStreamSource getResource(AbsoluteIri absoluteIri) {
            String path = URI.create(absoluteIri.toString()).toString();
            if (baseUri != null && !baseUri.isEmpty() && path.startsWith(baseUri)) {
                path = path.substring(baseUri.length());
            }
            String classpathFolder = JsonSchemaResourceUtils.generatedSchemasFolder(jsonSchemaConfiguration);
            String filePath = JsonSchemaResourceUtils.resolvePathWithinFolder(
                classpathFolder,
                path,
                absoluteIri.toString(),
                fallbackClasspathFolder
            );
            return () -> resourceLoader.getResourceAsStream(JsonSchemaResourceUtils.CLASSPATH_PREFIX + filePath)
                .orElseThrow(() -> new IllegalArgumentException("No schema found for uri: " + absoluteIri + " at path: " + filePath));
        }
    }
}

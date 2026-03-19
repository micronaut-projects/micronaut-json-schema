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

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.resource.InputStreamSource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.jsonschema.utils.JsonSchemaConfiguration;
import io.micronaut.jsonschema.utils.JsonSchemaResourceUtils;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Internal
final class DefaultJsonSchemaValidator implements JsonSchemaValidator {
    private final Map<Class<?>, Schema> jsonSchemaCache = new ConcurrentHashMap<>();
    private final JsonSchemaValidatorConfiguration config;
    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final SchemaRegistry schemaRegistry;
    private final JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader;

    DefaultJsonSchemaValidator(
        JsonSchemaValidatorConfiguration config,
        ResourceLoader resourceLoader,
        JsonMapper jsonMapper,
        SchemaRegistryConfig schemaRegistryConfig,
        JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader,
        JsonSchemaConfiguration jsonSchemaConfiguration
    ) {
        this.config = config;
        this.resourceLoader = resourceLoader;
        this.jsonMapper = jsonMapper;
        this.jsonSchemaClassPathResourceLoader = jsonSchemaClassPathResourceLoader;
        this.schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft202012(), builder -> builder
            .schemaRegistryConfig(schemaRegistryConfig)
            .resourceLoaders(resourceLoaders -> resourceLoaders.add(new ClasspathSchemaResourceLoader(jsonSchemaConfiguration))));
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(@NonNull String json, @NonNull Class<T> type) {
        Schema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, json);
    }

    @Override
    @NonNull
    public Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Map<String, Object> jsonSchema) throws IOException {
        Schema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    @NonNull
    public Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull String jsonSchema) throws IOException {
        Schema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    @NonNull
    public <T> Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Class<T> type) throws IOException {
        Schema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, value);
    }

    private <T> Schema jsonSchema(@NonNull Class<T> type) {
        String jsonSchema = jsonSchemaClassPathResourceLoader.jsonSchemaStringForClass(type).orElse(null);
        if (jsonSchema == null) {
            throw new IllegalArgumentException("No schema found for type: " + type);
        }
        return jsonSchema(jsonSchema);
    }

    @NonNull
    private Schema jsonSchema(@NonNull Map<String, Object> jsonSchema) {
        try {
            return jsonSchema(jsonMapper.writeValueAsString(jsonSchema));
        } catch (IOException e) {
            throw new IllegalArgumentException("could not serialize JSON Schema from: " + jsonSchema, e);
        }
    }

    @NonNull
    private Schema jsonSchema(@NonNull String jsonSchema) {
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
            messages.add(new ValidationMessageAdapter(error));
        }
        return messages;
    }

    private static final class ClasspathSchemaResourceLoader implements com.networknt.schema.resource.ResourceLoader {
        private final JsonSchemaConfiguration jsonSchemaConfiguration;
        private final String baseUri;
        private final String fallbackClasspathFolder;
        private final ResourceLoader resourceLoader;

        private ClasspathSchemaResourceLoader(JsonSchemaConfiguration jsonSchemaConfiguration,
                                             String baseUri,
                                             String fallbackClasspathFolder,
                                             ResourceLoader resourceLoader) {
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

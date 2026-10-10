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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.jsonschema.utils.JsonSchemaConfiguration;
import io.micronaut.jsonschema.utils.JsonSchemaResourceUtils;
import io.micronaut.jsonschema.validation.engine.CompiledJsonSchema;
import io.micronaut.jsonschema.validation.engine.Dialect;
import io.micronaut.jsonschema.validation.engine.JsonSchemaEngine;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Default {@link JsonSchemaValidator} backed by the built-in {@link JsonSchemaEngine}.
 * Schemas are compiled lazily on first use and cached: per type, and per schema string or map
 * for the ad-hoc variants (bounded).
 */
@Singleton
@Internal
final class DefaultJsonSchemaValidator implements JsonSchemaValidator {
    private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);
    private static final int MAX_CACHED_SCHEMAS = 256;

    private final Map<Class<?>, CompiledJsonSchema> typeSchemaCache = new ConcurrentHashMap<>();
    private final Map<Object, CompiledJsonSchema> schemaCache = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader;
    private final JsonSchemaEngine engine;

    DefaultJsonSchemaValidator(
        JsonSchemaValidatorConfiguration config,
        ResourceLoader resourceLoader,
        JsonMapper jsonMapper,
        JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader,
        JsonSchemaConfiguration jsonSchemaConfiguration
    ) {
        this.jsonMapper = jsonMapper;
        this.jsonSchemaClassPathResourceLoader = jsonSchemaClassPathResourceLoader;
        ClasspathSchemaRetriever retriever = new ClasspathSchemaRetriever(
            jsonSchemaConfiguration,
            config.baseUri(),
            config.classpathFolder(),
            resourceLoader,
            jsonMapper
        );
        this.engine = new JsonSchemaEngine(jsonMapper, retriever::retrieve, Dialect.DRAFT_2020_12, directoryUri(config.baseUri()));
    }

    private static @Nullable String directoryUri(@Nullable String baseUri) {
        if (baseUri == null || baseUri.isEmpty() || baseUri.endsWith("/")) {
            return baseUri;
        }
        // relative references in schemas without $id resolve to files inside the base URI "folder"
        return baseUri + "/";
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(String json, Class<T> type) throws IOException {
        return validate(schemaForType(type), parse(json));
    }

    @Override
    public Set<? extends ValidationMessage> validate(Object value, Map<String, Object> jsonSchema) throws IOException {
        JsonNode schemaNode = schemaTree(jsonSchema);
        CompiledJsonSchema schema = cached(schemaNode, () -> engine.compile(schemaNode));
        return validate(schema, tree(value));
    }

    @Override
    public Set<? extends ValidationMessage> validate(Object value, String jsonSchema) throws IOException {
        CompiledJsonSchema schema = schemaCache.get(jsonSchema);
        if (schema == null) {
            JsonNode schemaNode = parse(jsonSchema);
            schema = cached(jsonSchema, () -> engine.compile(schemaNode));
        }
        return validate(schema, tree(value));
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(Object value, Class<T> type) throws IOException {
        return validate(schemaForType(type), tree(value));
    }

    private CompiledJsonSchema schemaForType(Class<?> type) {
        CompiledJsonSchema schema = typeSchemaCache.get(type);
        if (schema == null) {
            schema = typeSchemaCache.computeIfAbsent(type, this::compileTypeSchema);
        }
        return schema;
    }

    private CompiledJsonSchema compileTypeSchema(Class<?> type) {
        String jsonSchema = jsonSchemaClassPathResourceLoader.jsonSchemaStringForClass(type).orElse(null);
        if (jsonSchema == null) {
            throw new IllegalArgumentException("No schema found for type: " + type);
        }
        try {
            return engine.compile(parse(jsonSchema));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse JSON Schema for type: " + type, e);
        }
    }

    private CompiledJsonSchema cached(Object key, Supplier<CompiledJsonSchema> compiler) {
        CompiledJsonSchema schema = schemaCache.get(key);
        if (schema != null) {
            return schema;
        }
        schema = compiler.get();
        if (schemaCache.size() >= MAX_CACHED_SCHEMAS) {
            schemaCache.clear();
        }
        schemaCache.put(key, schema);
        return schema;
    }

    private JsonNode schemaTree(Map<String, Object> jsonSchema) throws IOException {
        try {
            return JsonNode.from(jsonSchema);
        } catch (IllegalStateException e) {
            // the map contains values that are not plain JSON values, let the mapper convert them
            return jsonMapper.writeValueToTree(jsonSchema);
        }
    }

    private JsonNode tree(Object value) throws IOException {
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String json) {
            return parse(json);
        }
        return jsonMapper.writeValueToTree(value);
    }

    private JsonNode parse(String json) throws IOException {
        return jsonMapper.readValue(json, JSON_NODE);
    }

    private Set<? extends ValidationMessage> validate(CompiledJsonSchema schema, JsonNode value) {
        List<ValidationMessage> errors = engine.validate(schema, value);
        return errors.isEmpty() ? Set.of() : new LinkedHashSet<>(errors);
    }

    /**
     * Resolves schema URIs to JSON schema files on the classpath. URIs that start with the configured
     * base URI are resolved relative to the generated schemas folder.
     */
    private static final class ClasspathSchemaRetriever {
        private final JsonSchemaConfiguration jsonSchemaConfiguration;
        private final @Nullable String baseUri;
        private final String fallbackClasspathFolder;
        private final ResourceLoader resourceLoader;
        private final JsonMapper jsonMapper;

        private ClasspathSchemaRetriever(JsonSchemaConfiguration jsonSchemaConfiguration,
                                         @Nullable String baseUri,
                                         String fallbackClasspathFolder,
                                         ResourceLoader resourceLoader,
                                         JsonMapper jsonMapper) {
            this.jsonSchemaConfiguration = jsonSchemaConfiguration;
            this.baseUri = baseUri;
            this.fallbackClasspathFolder = fallbackClasspathFolder;
            this.resourceLoader = resourceLoader;
            this.jsonMapper = jsonMapper;
        }

        @Nullable JsonNode retrieve(String uri) throws IOException {
            String path = uri;
            if (baseUri != null && !baseUri.isEmpty() && path.startsWith(baseUri)) {
                path = path.substring(baseUri.length());
            }
            String classpathFolder = JsonSchemaResourceUtils.generatedSchemasFolder(jsonSchemaConfiguration);
            String filePath = JsonSchemaResourceUtils.resolvePathWithinFolder(
                classpathFolder,
                path,
                uri,
                fallbackClasspathFolder
            );
            Optional<InputStream> resource = resourceLoader.getResourceAsStream(JsonSchemaResourceUtils.CLASSPATH_PREFIX + filePath);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get()) {
                return jsonMapper.readValue(in, JSON_NODE);
            }
        }
    }
}

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
package io.micronaut.jsonschema.validation.engine;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.ValidationMessage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON Schema validator operating on Micronaut's {@link JsonNode} tree.
 *
 * <p>Supports the draft-04, draft-06, draft-07, 2019-09 and 2020-12 dialects. Schemas are compiled once
 * into an immutable graph that can be evaluated concurrently. Referenced documents are loaded through the
 * {@link SchemaRetriever} the first time they are needed and cached for the lifetime of the engine. The
 * meta-schemas of the supported dialects are bundled and resolved without network access.</p>
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
@Internal
public final class JsonSchemaEngine {

    /**
     * The base URI used for schemas that do not declare {@code $id} when no base URI was configured.
     */
    public static final String DEFAULT_BASE_URI = "urn:micronaut:json-schema:root";

    private static final String META_SCHEMA_RESOURCE_PREFIX = "META-INF/micronaut-json-schema-validation/metaschemas/";
    private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);

    final Map<String, SchemaResource> resources = new HashMap<>();
    final Map<String, Vocabularies> metaSchemas = new HashMap<>();
    final JsonMapper jsonMapper;
    final boolean assertFormats;

    private final SchemaRetriever retriever;
    private final Dialect defaultDialect;
    private final String defaultBaseUri;
    private final Object lock = new Object();

    /**
     * Creates an engine that asserts formats.
     *
     * @param jsonMapper The mapper used to parse the bundled meta-schemas
     * @param retriever The retriever used to load referenced schemas
     * @param defaultDialect The dialect used for schemas that do not declare {@code $schema}
     * @param defaultBaseUri The base URI used for schemas that do not declare {@code $id}, or null for {@link #DEFAULT_BASE_URI}
     */
    public JsonSchemaEngine(JsonMapper jsonMapper, SchemaRetriever retriever, Dialect defaultDialect, @Nullable String defaultBaseUri) {
        this(jsonMapper, retriever, defaultDialect, defaultBaseUri, true);
    }

    /**
     * Creates an engine.
     *
     * @param jsonMapper The mapper used to parse the bundled meta-schemas
     * @param retriever The retriever used to load referenced schemas
     * @param defaultDialect The dialect used for schemas that do not declare {@code $schema}
     * @param defaultBaseUri The base URI used for schemas that do not declare {@code $id}, or null for {@link #DEFAULT_BASE_URI}
     * @param assertFormats Whether the {@code format} keyword is an assertion. When false, formats are only asserted
     *                      for schemas whose meta-schema enables the 2020-12 format-assertion vocabulary
     */
    public JsonSchemaEngine(JsonMapper jsonMapper, SchemaRetriever retriever, Dialect defaultDialect, @Nullable String defaultBaseUri,
                            boolean assertFormats) {
        this.assertFormats = assertFormats;
        this.jsonMapper = jsonMapper;
        this.retriever = retriever;
        this.defaultDialect = defaultDialect;
        this.defaultBaseUri = defaultBaseUri == null || defaultBaseUri.isEmpty() ? DEFAULT_BASE_URI : defaultBaseUri;
    }

    /**
     * Compiles a schema whose base URI is the engine's default base URI.
     *
     * @param schema The schema
     * @return The compiled schema
     * @throws IllegalArgumentException If the schema is invalid
     */
    public CompiledJsonSchema compile(JsonNode schema) {
        return compile(schema, defaultBaseUri);
    }

    /**
     * Compiles a schema.
     *
     * @param schema The schema
     * @param baseUri The base URI the schema was retrieved from, used to resolve relative identifiers and references
     * @return The compiled schema
     * @throws IllegalArgumentException If the schema is invalid
     */
    public CompiledJsonSchema compile(JsonNode schema, String baseUri) {
        synchronized (lock) {
            return new SchemaCompiler(this).compileRoot(schema, baseUri, defaultDialect);
        }
    }

    /**
     * Validates an instance.
     *
     * @param schema The compiled schema
     * @param instance The instance
     * @return The validation errors, empty if the instance is valid
     */
    public List<ValidationMessage> validate(CompiledJsonSchema schema, JsonNode instance) {
        if (isValid(schema, instance)) {
            return List.of();
        }
        List<ValidationMessage> errors = new ArrayList<>(4);
        EvaluationContext ctx = new EvaluationContext(errors, schema.collectAnnotations);
        schema.root.evaluate(instance, ctx, ctx.newAnnotations());
        return errors;
    }

    /**
     * Checks whether an instance is valid, stopping at the first error.
     *
     * @param schema The compiled schema
     * @param instance The instance
     * @return true if valid
     */
    public boolean isValid(CompiledJsonSchema schema, JsonNode instance) {
        EvaluationContext ctx = new EvaluationContext(null, schema.collectAnnotations);
        return schema.root.evaluate(instance, ctx, ctx.newAnnotations());
    }

    static String resourceKey(Dialect dialect, String uri) {
        return dialect.ordinal() + "|" + uri;
    }

    /**
     * Loads a document, first from the bundled meta-schemas and then from the retriever.
     *
     * @param uri The absolute URI without fragment
     * @return The document or null
     */
    @Nullable JsonNode retrieve(String uri) {
        JsonNode metaSchema = bundledMetaSchema(uri);
        if (metaSchema != null) {
            return metaSchema;
        }
        try {
            return retriever.retrieve(uri);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load schema " + uri, e);
        }
    }

    private @Nullable JsonNode bundledMetaSchema(String uri) {
        String path;
        if (uri.startsWith("https://json-schema.org/")) {
            path = uri.substring("https://json-schema.org/".length());
        } else if (uri.startsWith("http://json-schema.org/")) {
            path = uri.substring("http://json-schema.org/".length());
        } else {
            return null;
        }
        if (path.endsWith("#")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty() || path.contains("..")) {
            return null;
        }
        ClassLoader classLoader = JsonSchemaEngine.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(META_SCHEMA_RESOURCE_PREFIX + path + ".json")) {
            if (in == null) {
                return null;
            }
            return jsonMapper.readValue(in, JSON_NODE);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read bundled meta-schema " + uri, e);
        }
    }
}

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

import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.engine.SchemaResource.Document;
import io.micronaut.jsonschema.validation.engine.SchemaResource.NodeInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Compiles schema documents into {@link Schema} graphs. One compiler is used per call to
 * {@link JsonSchemaEngine#compile} and always runs while holding the engine lock.
 * Documents retrieved by URI are registered globally in the engine and shared by all
 * compiled schemas; the document being compiled is only visible to this compilation.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class SchemaCompiler {

    private static final int MAX_META_SCHEMA_DEPTH = 8;
    private static final String REF = "$ref";
    private static final String DYNAMIC_REF = "$dynamicRef";
    private static final String RECURSIVE_REF = "$recursiveRef";
    private static final String ALL_OF = "allOf";
    private static final String ANY_OF = "anyOf";
    private static final String ONE_OF = "oneOf";
    private static final String NOT = "not";
    private static final String IF = "if";
    private static final String THEN = "then";
    private static final String ELSE = "else";
    private static final String PROPERTIES = "properties";
    private static final String PATTERN_PROPERTIES = "patternProperties";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String PROPERTY_NAMES = "propertyNames";
    private static final String DEPENDENT_SCHEMAS = "dependentSchemas";
    private static final String DEPENDENCIES = "dependencies";
    private static final String ITEMS = "items";
    private static final String PREFIX_ITEMS = "prefixItems";
    private static final String ADDITIONAL_ITEMS = "additionalItems";
    private static final String CONTAINS = "contains";
    private static final String UNEVALUATED_PROPERTIES = "unevaluatedProperties";
    private static final String UNEVALUATED_ITEMS = "unevaluatedItems";
    private static final String MAXIMUM = "maximum";
    private static final String MINIMUM = "minimum";
    private static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";
    private static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
    private static final String CONTENT_ENCODING = "contentEncoding";
    private static final String CONTENT_MEDIA_TYPE = "contentMediaType";

    private final JsonSchemaEngine engine;
    private final Map<String, SchemaResource> localResources = new HashMap<>();
    private @Nullable Document rootDocument;
    private final List<Runnable> rollback = new ArrayList<>();
    private final ArrayDeque<SchemaResource> pending = new ArrayDeque<>();
    private final Set<SchemaResource> touched = Collections.newSetFromMap(new IdentityHashMap<>());

    SchemaCompiler(JsonSchemaEngine engine) {
        this.engine = engine;
    }

    /**
     * Compiles a root schema document.
     *
     * @param root The schema
     * @param baseUri The base (retrieval) URI of the document
     * @param defaultDialect The dialect used when the schema does not declare {@code $schema}
     * @return The compiled schema
     */
    CompiledJsonSchema compileRoot(JsonNode root, String baseUri, Dialect defaultDialect) {
        try {
            Document document = new Document(baseUri, root);
            rootDocument = document;
            index(document, root, baseUri, Vocabularies.of(defaultDialect), null, "", localResources::put);
            SchemaResource resource = rootResource(document);
            localResources.putIfAbsent(baseUri, resource);
            Schema schema = compile(root, new NodeInfo(resource, ""));
            drain();
            rollback.clear();
            return new CompiledJsonSchema(schema, needsAnnotations(schema));
        } catch (RuntimeException e) {
            for (int i = rollback.size() - 1; i >= 0; i--) {
                rollback.get(i).run();
            }
            rollback.clear();
            throw e;
        }
    }

    // ---- indexing ----

    private static SchemaResource rootResource(Document document) {
        NodeInfo info = document.root.isObject() ? document.nodes.get(document.root) : null;
        if (info != null) {
            return info.resource();
        }
        SchemaResource resource = document.resources.get(document.uri);
        if (resource == null) {
            throw new IllegalStateException("Document " + document.uri + " was not indexed");
        }
        return resource;
    }

    private void index(Document document, JsonNode node, String base, Vocabularies vocabularies,
                       @Nullable SchemaResource resource, String pointer, Registration registration) {
        if (!node.isObject()) {
            if (resource == null) {
                SchemaResource booleanResource = new SchemaResource(base, node, vocabularies, document);
                document.resources.put(base, booleanResource);
                registration.register(base, booleanResource);
            }
            return;
        }
        Vocabularies vocab = resourceVocabularies(node, vocabularies, resource == null);
        Dialect dialect = vocab.dialect();
        String id = identifier(node, dialect);
        String idBase = id == null ? "" : Uris.stripFragment(id);
        String newBase = idBase.isEmpty() ? base : Uris.resolve(base, idBase);
        SchemaResource current = resource;
        String currentPointer = pointer;
        if (current == null || !idBase.isEmpty()) {
            current = new SchemaResource(newBase, node, vocab, document);
            document.resources.put(newBase, current);
            registration.register(newBase, current);
            currentPointer = "";
        }
        document.nodes.put(node, new NodeInfo(current, currentPointer));
        registerAnchors(node, current, dialect, id);
        if (dialect == Dialect.DRAFT_2019_09 && currentPointer.isEmpty()) {
            registerRecursiveAnchor(node, current);
        }
        IndexScope scope = new IndexScope(document, newBase, vocab, current, registration);
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            indexKeyword(scope, entry.getKey(), entry.getValue(), currentPointer + "/" + Uris.escapePointerToken(entry.getKey()));
        }
    }

    /**
     * Determines the vocabularies of a schema object, which may declare its own {@code $schema} if it is a resource root.
     */
    private Vocabularies resourceVocabularies(JsonNode node, Vocabularies vocabularies, boolean documentRoot) {
        JsonNode schemaKeyword = node.get("$schema");
        boolean resourceRoot = documentRoot || (vocabularies.dialect().atLeast(Dialect.DRAFT_2019_09) && node.get("$id") != null);
        if (resourceRoot && schemaKeyword != null && schemaKeyword.isString()) {
            return vocabulariesFor(schemaKeyword.getStringValue(), 0);
        }
        return vocabularies;
    }

    /**
     * @return The identifier of a schema object, or null if it has none (or if draft-07 and earlier ignore it next to {@code $ref})
     */
    private static @Nullable String identifier(JsonNode node, Dialect dialect) {
        JsonNode idNode = node.get(dialect.idKeyword());
        boolean ignored = dialect.refOverridesSiblings() && node.get(REF) != null;
        return idNode != null && idNode.isString() && !ignored ? idNode.getStringValue() : null;
    }

    private static void registerAnchors(JsonNode node, SchemaResource resource, Dialect dialect, @Nullable String id) {
        String idFragment = id == null ? null : Uris.fragment(id);
        if (idFragment != null && !idFragment.isEmpty() && !dialect.atLeast(Dialect.DRAFT_2019_09)) {
            // draft-07 and earlier: plain-name fragments in $id are anchors
            resource.anchors.put(idFragment, node);
        }
        String anchor = dialect.atLeast(Dialect.DRAFT_2019_09) ? stringValue(node, "$anchor") : null;
        if (anchor != null) {
            resource.anchors.put(anchor, node);
        }
        String dynamicAnchor = dialect == Dialect.DRAFT_2020_12 ? stringValue(node, "$dynamicAnchor") : null;
        if (dynamicAnchor != null) {
            resource.anchors.putIfAbsent(dynamicAnchor, node);
            resource.dynamicAnchorNodes.put(dynamicAnchor, node);
        }
    }

    private static void registerRecursiveAnchor(JsonNode node, SchemaResource resource) {
        JsonNode recursiveAnchor = node.get("$recursiveAnchor");
        if (recursiveAnchor != null && recursiveAnchor.isBoolean() && recursiveAnchor.getBooleanValue()) {
            resource.recursiveAnchor = true;
        }
    }

    private static @Nullable String stringValue(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && value.isString() ? value.getStringValue() : null;
    }

    private void indexKeyword(IndexScope scope, String keyword, JsonNode value, String pointer) {
        switch (keyword) {
            case ADDITIONAL_PROPERTIES, ADDITIONAL_ITEMS, UNEVALUATED_PROPERTIES, UNEVALUATED_ITEMS, CONTAINS,
                 PROPERTY_NAMES, NOT, IF, THEN, ELSE, "contentSchema" -> indexSubschema(scope, value, pointer);
            case ITEMS -> {
                if (value.isArray()) {
                    indexArray(scope, value, pointer);
                } else {
                    indexSubschema(scope, value, pointer);
                }
            }
            case ALL_OF, ANY_OF, ONE_OF, PREFIX_ITEMS -> indexArray(scope, value, pointer);
            case PROPERTIES, PATTERN_PROPERTIES, "$defs", "definitions", DEPENDENT_SCHEMAS, DEPENDENCIES -> indexMap(scope, value, pointer);
            default -> {
                // not a subschema location
            }
        }
    }

    private void indexSubschema(IndexScope scope, JsonNode value, String pointer) {
        index(scope.document(), value, scope.base(), scope.vocabularies(), scope.resource(), pointer, scope.registration());
    }

    private void indexArray(IndexScope scope, JsonNode array, String pointer) {
        if (!array.isArray()) {
            return;
        }
        int i = 0;
        for (JsonNode value : array.values()) {
            indexSubschema(scope, value, pointer + "/" + i++);
        }
    }

    private void indexMap(IndexScope scope, JsonNode map, String pointer) {
        if (!map.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> child : map.entries()) {
            if (!child.getValue().isArray()) {
                indexSubschema(scope, child.getValue(), pointer + "/" + Uris.escapePointerToken(child.getKey()));
            }
        }
    }

    private Vocabularies vocabulariesFor(String metaSchemaUri, int depth) {
        Dialect dialect = Dialect.fromMetaSchemaUri(metaSchemaUri);
        if (dialect != null) {
            return Vocabularies.of(dialect);
        }
        String uri = Uris.stripFragment(metaSchemaUri);
        Vocabularies cached = engine.metaSchemas.get(uri);
        if (cached != null) {
            return cached;
        }
        if (depth > MAX_META_SCHEMA_DEPTH) {
            throw new IllegalArgumentException("Meta-schema chain too deep resolving $schema " + metaSchemaUri);
        }
        JsonNode metaSchema;
        try {
            metaSchema = engine.retrieve(uri);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unsupported $schema '" + metaSchemaUri + "': the meta-schema could not be loaded", e);
        }
        if (metaSchema == null || !metaSchema.isObject()) {
            throw new IllegalArgumentException("Unsupported $schema '" + metaSchemaUri + "': the meta-schema could not be loaded");
        }
        JsonNode parent = metaSchema.get("$schema");
        if (parent == null || !parent.isString() || Uris.stripFragment(parent.getStringValue()).equals(uri)) {
            throw new IllegalArgumentException("Unsupported $schema '" + metaSchemaUri + "': the meta-schema does not declare a known dialect");
        }
        Dialect base = vocabulariesFor(parent.getStringValue(), depth + 1).dialect();
        JsonNode vocabulary = metaSchema.get("$vocabulary");
        Vocabularies vocabularies = vocabulary != null && vocabulary.isObject() && base.atLeast(Dialect.DRAFT_2019_09)
            ? Vocabularies.of(base, vocabulary)
            : Vocabularies.of(base);
        engine.metaSchemas.put(uri, vocabularies);
        rollback.add(() -> engine.metaSchemas.remove(uri));
        return vocabularies;
    }

    // ---- resolution ----

    private @Nullable SchemaResource findResource(String uri, Dialect contextDialect, boolean fromRootDocument) {
        // shared (retrieved) documents must never resolve into the private resources of the schema being compiled,
        // since their compiled graph outlives this compilation and is reused by other schemas
        SchemaResource resource = fromRootDocument ? localResources.get(uri) : null;
        if (resource != null) {
            return resource;
        }
        String key = JsonSchemaEngine.resourceKey(contextDialect, uri);
        resource = engine.resources.get(key);
        if (resource != null) {
            return resource;
        }
        JsonNode json;
        try {
            json = engine.retrieve(uri);
        } catch (RuntimeException e) {
            throw new UnresolvableReferenceException("Unable to load schema " + uri + ": " + e.getMessage(), e);
        }
        if (json == null) {
            return null;
        }
        Document document = new Document(uri, json);
        Registration registration = (resourceUri, schemaResource) -> {
            String resourceKey = JsonSchemaEngine.resourceKey(contextDialect, resourceUri);
            if (engine.resources.putIfAbsent(resourceKey, schemaResource) == null) {
                rollback.add(() -> engine.resources.remove(resourceKey));
            }
        };
        index(document, json, uri, Vocabularies.of(contextDialect), null, "", registration);
        registration.register(uri, rootResource(document));
        return engine.resources.get(key);
    }

    private Schema resolve(Schema owner, String reference) {
        String absolute = Uris.resolve(owner.resource.uri, reference);
        String base = Uris.stripFragment(absolute);
        String fragment = Uris.fragment(absolute);
        SchemaResource resource = findResource(base, owner.resource.dialect(), owner.resource.document == rootDocument);
        if (resource == null) {
            throw unresolvable(owner, reference, absolute, "no schema found for " + base);
        }
        if (fragment == null || fragment.isEmpty()) {
            return compile(resource.root, new NodeInfo(resource, ""));
        }
        String decoded = Uris.percentDecode(fragment);
        if (decoded.startsWith("/")) {
            return resolvePointer(owner, reference, absolute, resource, decoded);
        }
        JsonNode anchored = resource.anchors.get(decoded);
        if (anchored == null) {
            throw unresolvable(owner, reference, absolute, "no anchor named '" + decoded + "'");
        }
        NodeInfo info = resource.document.nodes.get(anchored);
        return compile(anchored, info != null ? info : new NodeInfo(resource, ""));
    }

    private Schema resolvePointer(Schema owner, String reference, String absolute, SchemaResource resource, String pointer) {
        JsonNode current = resource.root;
        NodeInfo info = new NodeInfo(resource, "");
        for (String token : Uris.pointerTokens(pointer)) {
            JsonNode next = child(current, token);
            if (next == null) {
                throw unresolvable(owner, reference, absolute, "no value at JSON pointer " + pointer);
            }
            // keep the base URI of embedded resources the pointer passes through
            NodeInfo known = next.isObject() ? resource.document.nodes.get(next) : null;
            info = known != null ? known : new NodeInfo(info.resource(), info.pointer() + "/" + Uris.escapePointerToken(token));
            current = next;
        }
        return compile(current, info);
    }

    private static @Nullable JsonNode child(JsonNode node, String token) {
        if (node.isObject()) {
            return node.get(token);
        }
        if (node.isArray()) {
            int index = Uris.parseIndex(token);
            return index < 0 ? null : node.get(index);
        }
        return null;
    }

    private static UnresolvableReferenceException unresolvable(Schema owner, String reference, String absolute, String reason) {
        return new UnresolvableReferenceException("Unable to resolve reference '" + reference + "' (" + absolute + ") from " + owner.location
            + ": " + reason, null);
    }

    private Keyword reference(Schema owner, String name, String reference) {
        Schema target = null;
        RuntimeException failure = null;
        try {
            target = resolve(owner, reference);
        } catch (UnresolvableReferenceException e) {
            failure = e;
        }
        if (name.equals(DYNAMIC_REF)) {
            String anchor = null;
            String fragment = Uris.fragment(reference);
            if (target != null && fragment != null && !fragment.isEmpty() && !fragment.startsWith("/")) {
                JsonNode anchorNode = target.resource.dynamicAnchorNodes.get(fragment);
                if (anchorNode != null && target.resource.document.compiled.get(anchorNode) == target) {
                    anchor = fragment;
                }
            }
            return new ReferenceKeywords.DynamicRef(owner, reference, target, failure, anchor);
        }
        if (name.equals(RECURSIVE_REF)) {
            boolean dynamic = target != null && target.resource.recursiveAnchor && target.resource.rootSchema == target;
            return new ReferenceKeywords.RecursiveRef(owner, reference, target, failure, dynamic);
        }
        return new ReferenceKeywords.Ref(owner, name, reference, target, failure);
    }

    // ---- compilation ----

    private static String location(NodeInfo info) {
        return info.resource().uri + "#" + info.pointer();
    }

    private Schema compile(JsonNode node, NodeInfo context) {
        if (node.isBoolean()) {
            return new Schema(context.resource(), location(context), node.getBooleanValue());
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Invalid schema at " + location(context) + ": expected an object or a boolean but found " + JsonValues.typeName(node));
        }
        Document document = context.resource().document;
        Schema existing = document.compiled.get(node);
        if (existing != null) {
            return existing;
        }
        NodeInfo info = document.nodes.getOrDefault(node, context);
        SchemaResource resource = info.resource();
        Schema schema = new Schema(resource, location(info), null);
        document.compiled.put(node, schema);
        rollback.add(() -> document.compiled.remove(node));
        if (info.pointer().isEmpty() && resource.rootSchema == null) {
            resource.rootSchema = schema;
            rollback.add(() -> resource.rootSchema = null);
        }
        if (touched.add(resource) && (resource.recursiveAnchor || !resource.dynamicAnchorNodes.isEmpty())) {
            pending.add(resource);
        }
        schema.keywords = compileKeywords(node, schema, info);
        return schema;
    }

    private void drain() {
        while (!pending.isEmpty()) {
            SchemaResource resource = pending.poll();
            if (resource.recursiveAnchor && resource.rootSchema == null) {
                compile(resource.root, new NodeInfo(resource, ""));
            }
            for (Map.Entry<String, JsonNode> entry : resource.dynamicAnchorNodes.entrySet()) {
                String name = entry.getKey();
                if (!resource.dynamicAnchors.containsKey(name)) {
                    NodeInfo info = resource.document.nodes.get(entry.getValue());
                    Schema schema = compile(entry.getValue(), info != null ? info : new NodeInfo(resource, ""));
                    resource.dynamicAnchors.put(name, schema);
                    rollback.add(() -> resource.dynamicAnchors.remove(name));
                }
            }
        }
    }

    private Schema sub(JsonNode value, NodeInfo parent, String keyword) {
        return compile(value, new NodeInfo(parent.resource(), parent.pointer() + "/" + Uris.escapePointerToken(keyword)));
    }

    private Schema sub(JsonNode value, NodeInfo parent, String keyword, String child) {
        return compile(value, new NodeInfo(parent.resource(),
            parent.pointer() + "/" + Uris.escapePointerToken(keyword) + "/" + Uris.escapePointerToken(child)));
    }

    private Schema[] subArray(JsonNode array, NodeInfo parent, String keyword) {
        Schema[] schemas = new Schema[array.size()];
        int i = 0;
        for (JsonNode value : array.values()) {
            schemas[i] = sub(value, parent, keyword, Integer.toString(i));
            i++;
        }
        return schemas;
    }

    private Keyword[] compileKeywords(JsonNode node, Schema schema, NodeInfo info) {
        Vocabularies vocab = info.resource().vocabularies;
        JsonNode ref = node.get(REF);
        if (vocab.dialect().refOverridesSiblings() && ref != null) {
            return ref.isString() ? new Keyword[]{reference(schema, REF, ref.getStringValue())} : new Keyword[0];
        }
        KeywordContext context = new KeywordContext(node, schema, info, vocab, new HashMap<>());
        List<Keyword> keywords = new ArrayList<>(node.size());
        List<Keyword> late = new ArrayList<>(0);
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            Keyword compiled = compileKeyword(entry.getKey(), entry.getValue(), context);
            if (compiled != null) {
                keywords.add(compiled);
            } else {
                Keyword unevaluated = compileUnevaluated(entry.getKey(), entry.getValue(), context);
                if (unevaluated != null) {
                    late.add(unevaluated);
                }
            }
        }
        // unevaluated* need the annotations of all other keywords of the schema object
        late.sort(Comparator.comparing(keyword -> keyword.name));
        keywords.addAll(late);
        return keywords.toArray(new Keyword[0]);
    }

    private @Nullable Keyword compileKeyword(String keyword, JsonNode value, KeywordContext context) {
        Vocabularies vocab = context.vocabularies();
        Keyword compiled = compileCore(keyword, value, context.schema());
        if (compiled == null && vocab.applicator()) {
            compiled = compileApplicator(keyword, value, context);
        }
        if (compiled == null && vocab.validation()) {
            compiled = compileValidation(keyword, value, context);
        }
        if (compiled == null && keyword.equals("format") && (engine.assertFormats || vocab.formatAssertion())) {
            compiled = format(value, context);
        }
        if (compiled == null && vocab.dialect() == Dialect.DRAFT_7 && (keyword.equals(CONTENT_MEDIA_TYPE) || keyword.equals(CONTENT_ENCODING))) {
            compiled = content(keyword, value, context);
        }
        return compiled;
    }

    private @Nullable Keyword compileUnevaluated(String keyword, JsonNode value, KeywordContext context) {
        boolean properties = keyword.equals(UNEVALUATED_PROPERTIES);
        if (!(properties || keyword.equals(UNEVALUATED_ITEMS)) || !unevaluatedEnabled(context.vocabularies())) {
            return null;
        }
        Schema schema = context.schema();
        Schema subschema = sub(value, context.info(), keyword);
        schema.hasUnevaluated = true;
        return properties
            ? new ApplicatorKeywords.UnevaluatedProperties(schema, subschema)
            : new ApplicatorKeywords.UnevaluatedItems(schema, subschema);
    }

    private static boolean unevaluatedEnabled(Vocabularies vocab) {
        return switch (vocab.dialect()) {
            case DRAFT_2019_09 -> vocab.applicator();
            case DRAFT_2020_12 -> vocab.unevaluated();
            default -> false;
        };
    }

    private @Nullable Keyword compileCore(String keyword, JsonNode value, Schema schema) {
        if (!value.isString()) {
            return null;
        }
        Dialect dialect = schema.resource.dialect();
        return switch (keyword) {
            case REF -> reference(schema, REF, value.getStringValue());
            case DYNAMIC_REF -> dialect == Dialect.DRAFT_2020_12 ? reference(schema, DYNAMIC_REF, value.getStringValue()) : null;
            case RECURSIVE_REF -> dialect == Dialect.DRAFT_2019_09 ? reference(schema, RECURSIVE_REF, value.getStringValue()) : null;
            default -> null;
        };
    }

    private @Nullable Keyword compileApplicator(String keyword, JsonNode value, KeywordContext context) {
        Schema schema = context.schema();
        NodeInfo info = context.info();
        Dialect dialect = context.dialect();
        return switch (keyword) {
            case ALL_OF -> value.isArray() ? new ApplicatorKeywords.AllOf(schema, subArray(value, info, keyword)) : null;
            case ANY_OF -> value.isArray() ? new ApplicatorKeywords.AnyOf(schema, subArray(value, info, keyword), value) : null;
            case ONE_OF -> value.isArray() ? new ApplicatorKeywords.OneOf(schema, subArray(value, info, keyword)) : null;
            case NOT -> new ApplicatorKeywords.Not(schema, sub(value, info, keyword), value);
            case IF -> dialect.atLeast(Dialect.DRAFT_7) ? ifThenElse(value, context) : null;
            case PROPERTIES -> value.isObject() ? properties(value, context) : null;
            case PATTERN_PROPERTIES -> value.isObject() ? patternProperties(value, context) : null;
            case ADDITIONAL_PROPERTIES -> additionalProperties(value, context);
            case PROPERTY_NAMES -> dialect.atLeast(Dialect.DRAFT_6) ? new ApplicatorKeywords.PropertyNames(schema, sub(value, info, keyword)) : null;
            case DEPENDENT_SCHEMAS -> dialect.atLeast(Dialect.DRAFT_2019_09) && value.isObject() ? dependentSchemas(value, context) : null;
            case DEPENDENCIES -> value.isObject() ? dependencies(value, context) : null;
            case ITEMS -> items(value, context);
            case PREFIX_ITEMS -> dialect == Dialect.DRAFT_2020_12 && value.isArray()
                ? new ApplicatorKeywords.PrefixItems(schema, keyword, subArray(value, info, keyword))
                : null;
            case ADDITIONAL_ITEMS -> additionalItems(value, context);
            case CONTAINS -> dialect.atLeast(Dialect.DRAFT_6) ? contains(value, context) : null;
            default -> null;
        };
    }

    private Keyword ifThenElse(JsonNode value, KeywordContext context) {
        NodeInfo info = context.info();
        JsonNode then = context.node().get(THEN);
        JsonNode otherwise = context.node().get(ELSE);
        return new ApplicatorKeywords.IfThenElse(context.schema(), sub(value, info, IF),
            then != null ? sub(then, info, THEN) : null,
            otherwise != null ? sub(otherwise, info, ELSE) : null);
    }

    private Keyword properties(JsonNode value, KeywordContext context) {
        String[] names = new String[value.size()];
        Schema[] schemas = new Schema[value.size()];
        int i = 0;
        for (Map.Entry<String, JsonNode> property : value.entries()) {
            names[i] = property.getKey();
            schemas[i] = sub(property.getValue(), context.info(), PROPERTIES, property.getKey());
            i++;
        }
        return new ApplicatorKeywords.Properties(context.schema(), names, schemas);
    }

    private Keyword patternProperties(JsonNode value, KeywordContext context) {
        Pattern[] patterns = new Pattern[value.size()];
        Schema[] schemas = new Schema[value.size()];
        int i = 0;
        for (Map.Entry<String, JsonNode> property : value.entries()) {
            patterns[i] = pattern(property.getKey(), context);
            schemas[i] = sub(property.getValue(), context.info(), PATTERN_PROPERTIES, property.getKey());
            i++;
        }
        return new ApplicatorKeywords.PatternProperties(context.schema(), patterns, schemas);
    }

    private Keyword additionalProperties(JsonNode value, KeywordContext context) {
        Set<String> names = new HashSet<>();
        JsonNode properties = context.node().get(PROPERTIES);
        if (properties != null && properties.isObject()) {
            properties.entries().forEach(property -> names.add(property.getKey()));
        }
        List<Pattern> patterns = new ArrayList<>();
        JsonNode patternProperties = context.node().get(PATTERN_PROPERTIES);
        if (patternProperties != null && patternProperties.isObject()) {
            patternProperties.entries().forEach(property -> patterns.add(pattern(property.getKey(), context)));
        }
        return new ApplicatorKeywords.AdditionalProperties(context.schema(), names, patterns.toArray(new Pattern[0]),
            sub(value, context.info(), ADDITIONAL_PROPERTIES));
    }

    private Keyword dependentSchemas(JsonNode value, KeywordContext context) {
        List<String> names = new ArrayList<>();
        List<Schema> schemas = new ArrayList<>();
        for (Map.Entry<String, JsonNode> dependency : value.entries()) {
            names.add(dependency.getKey());
            schemas.add(sub(dependency.getValue(), context.info(), DEPENDENT_SCHEMAS, dependency.getKey()));
        }
        return new ApplicatorKeywords.DependentSchemas(context.schema(), DEPENDENT_SCHEMAS, names.toArray(new String[0]), schemas.toArray(new Schema[0]));
    }

    private @Nullable Keyword items(JsonNode value, KeywordContext context) {
        Schema schema = context.schema();
        NodeInfo info = context.info();
        if (context.dialect() != Dialect.DRAFT_2020_12) {
            return value.isArray()
                ? new ApplicatorKeywords.PrefixItems(schema, ITEMS, subArray(value, info, ITEMS))
                : new ApplicatorKeywords.Items(schema, ITEMS, 0, sub(value, info, ITEMS));
        }
        if (value.isArray()) {
            return null;
        }
        JsonNode prefixItems = context.node().get(PREFIX_ITEMS);
        int start = prefixItems != null && prefixItems.isArray() ? prefixItems.size() : 0;
        return new ApplicatorKeywords.Items(schema, ITEMS, start, sub(value, info, ITEMS));
    }

    private @Nullable Keyword additionalItems(JsonNode value, KeywordContext context) {
        JsonNode items = context.node().get(ITEMS);
        if (context.dialect() == Dialect.DRAFT_2020_12 || items == null || !items.isArray()) {
            return null;
        }
        return new ApplicatorKeywords.Items(context.schema(), ADDITIONAL_ITEMS, items.size(), sub(value, context.info(), ADDITIONAL_ITEMS));
    }

    private Keyword contains(JsonNode value, KeywordContext context) {
        boolean bounded = context.dialect().atLeast(Dialect.DRAFT_2019_09);
        JsonNode minContains = bounded ? context.node().get("minContains") : null;
        JsonNode maxContains = bounded ? context.node().get("maxContains") : null;
        boolean explicitMin = minContains != null && minContains.isNumber();
        long min = explicitMin ? limit(minContains.getNumberValue()) : 1;
        long max = maxContains != null && maxContains.isNumber() ? limit(maxContains.getNumberValue()) : -1;
        return new ApplicatorKeywords.Contains(context.schema(), sub(value, context.info(), CONTAINS), value, min, explicitMin, max,
            context.dialect() == Dialect.DRAFT_2020_12);
    }

    private Keyword dependencies(JsonNode value, KeywordContext context) {
        Schema schema = context.schema();
        Map<String, String[]> required = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        List<Schema> schemas = new ArrayList<>();
        for (Map.Entry<String, JsonNode> dependency : value.entries()) {
            JsonNode dependencyValue = dependency.getValue();
            if (dependencyValue.isArray()) {
                required.put(dependency.getKey(), strings(dependencyValue));
            } else {
                names.add(dependency.getKey());
                schemas.add(sub(dependencyValue, context.info(), DEPENDENCIES, dependency.getKey()));
            }
        }
        Keyword schemaDependencies = new ApplicatorKeywords.DependentSchemas(schema, DEPENDENCIES, names.toArray(new String[0]), schemas.toArray(new Schema[0]));
        if (required.isEmpty()) {
            return schemaDependencies;
        }
        Keyword requiredDependencies = new ValidationKeywords.DependentRequired(schema, DEPENDENCIES, required);
        if (names.isEmpty()) {
            return requiredDependencies;
        }
        return new ApplicatorKeywords.AllOfKeywords(schema, DEPENDENCIES, new Keyword[]{requiredDependencies, schemaDependencies});
    }

    private static @Nullable Keyword format(JsonNode value, KeywordContext context) {
        if (!value.isString()) {
            return null;
        }
        Predicate<String> validator = Formats.forName(value.getStringValue(), context.dialect());
        return validator != null ? new ValidationKeywords.Format(context.schema(), value.getStringValue(), validator) : null;
    }

    private @Nullable Keyword content(String keyword, JsonNode value, KeywordContext context) {
        if (!value.isString()) {
            return null;
        }
        JsonNode encoding = context.node().get(CONTENT_ENCODING);
        boolean base64 = encoding != null && encoding.isString() && encoding.getStringValue().equalsIgnoreCase("base64");
        if (keyword.equals(CONTENT_ENCODING)) {
            return base64 ? new ValidationKeywords.ContentEncoding(context.schema(), value.getStringValue()) : null;
        }
        String mediaType = value.getStringValue().toLowerCase(Locale.ROOT);
        int semicolon = mediaType.indexOf(';');
        if (semicolon >= 0) {
            mediaType = mediaType.substring(0, semicolon).trim();
        }
        if (mediaType.equals("application/json") || mediaType.endsWith("+json")) {
            return new ValidationKeywords.ContentMediaType(context.schema(), value.getStringValue(), base64, engine.jsonMapper);
        }
        return null;
    }

    private static String[] strings(JsonNode array) {
        List<String> values = new ArrayList<>(array.size());
        for (JsonNode value : array.values()) {
            if (value.isString()) {
                values.add(value.getStringValue());
            }
        }
        return values.toArray(new String[0]);
    }

    private static Pattern pattern(String regex, KeywordContext context) {
        return context.patterns().computeIfAbsent(regex, r -> {
            try {
                return EcmaRegex.compile(r);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid regular expression '" + r + "' in schema " + context.schema().location + ": " + e.getMessage(), e);
            }
        });
    }

    private @Nullable Keyword compileValidation(String keyword, JsonNode value, KeywordContext context) {
        Schema schema = context.schema();
        Dialect dialect = context.dialect();
        return switch (keyword) {
            case "type" -> type(value, schema, dialect == Dialect.DRAFT_4);
            case "enum" -> value.isArray() ? new ValidationKeywords.Enum(schema, value) : null;
            case "const" -> dialect.atLeast(Dialect.DRAFT_6) ? new ValidationKeywords.Const(schema, value) : null;
            case "multipleOf" -> value.isNumber() ? new ValidationKeywords.MultipleOf(schema, value.getNumberValue()) : null;
            case MAXIMUM, MINIMUM -> value.isNumber() ? bound(keyword, value, context) : null;
            case EXCLUSIVE_MAXIMUM, EXCLUSIVE_MINIMUM -> value.isNumber() && dialect.atLeast(Dialect.DRAFT_6)
                ? new ValidationKeywords.Bound(schema, keyword, value.getNumberValue(), keyword.equals(EXCLUSIVE_MAXIMUM), true)
                : null;
            case "maxLength", "minLength" -> value.isNumber()
                ? new ValidationKeywords.Length(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxLength"))
                : null;
            case "pattern" -> value.isString()
                ? new ValidationKeywords.PatternKeyword(schema, pattern(value.getStringValue(), context), value.getStringValue())
                : null;
            case "maxItems", "minItems" -> value.isNumber()
                ? new ValidationKeywords.ItemCount(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxItems"))
                : null;
            case "uniqueItems" -> value.isBoolean() && value.getBooleanValue() ? new ValidationKeywords.UniqueItems(schema) : null;
            case "maxProperties", "minProperties" -> value.isNumber()
                ? new ValidationKeywords.PropertyCount(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxProperties"))
                : null;
            case "required" -> value.isArray() ? new ValidationKeywords.Required(schema, strings(value)) : null;
            case "dependentRequired" -> dialect.atLeast(Dialect.DRAFT_2019_09) && value.isObject() ? dependentRequired(keyword, value, schema) : null;
            default -> null;
        };
    }

    private static Keyword bound(String keyword, JsonNode value, KeywordContext context) {
        boolean maximum = keyword.equals(MAXIMUM);
        boolean exclusive = false;
        if (context.dialect() == Dialect.DRAFT_4) {
            // draft-04: exclusiveMaximum and exclusiveMinimum are boolean modifiers
            JsonNode flag = context.node().get(maximum ? EXCLUSIVE_MAXIMUM : EXCLUSIVE_MINIMUM);
            exclusive = flag != null && flag.isBoolean() && flag.getBooleanValue();
        }
        return new ValidationKeywords.Bound(context.schema(), keyword, value.getNumberValue(), maximum, exclusive);
    }

    private static Keyword dependentRequired(String keyword, JsonNode value, Schema schema) {
        Map<String, String[]> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> dependency : value.entries()) {
            if (dependency.getValue().isArray()) {
                dependencies.put(dependency.getKey(), strings(dependency.getValue()));
            }
        }
        return new ValidationKeywords.DependentRequired(schema, keyword, dependencies);
    }

    /**
     * Converts a non-negative integer keyword value to a long, saturating values that do not fit.
     */
    private static long limit(Number value) {
        if (JsonValues.compare(value, Long.MAX_VALUE) >= 0) {
            return Long.MAX_VALUE;
        }
        if (JsonValues.compare(value, Long.MIN_VALUE) <= 0) {
            return Long.MIN_VALUE;
        }
        return value.longValue();
    }

    private static @Nullable Keyword type(JsonNode value, Schema schema, boolean strictInteger) {
        if (value.isString()) {
            int bit = ValidationKeywords.typeBit(value.getStringValue());
            return bit == 0 ? null : new ValidationKeywords.Type(schema, bit, value.getStringValue(), strictInteger);
        }
        if (value.isArray()) {
            int bits = 0;
            List<String> names = new ArrayList<>();
            for (JsonNode type : value.values()) {
                if (type.isString()) {
                    bits |= ValidationKeywords.typeBit(type.getStringValue());
                    names.add(type.getStringValue());
                }
            }
            return new ValidationKeywords.Type(schema, bits, names.toString(), strictInteger);
        }
        return null;
    }

    private static boolean needsAnnotations(Schema root) {
        Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Schema> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Schema schema = stack.pop();
            if (!visited.add(schema)) {
                continue;
            }
            if (schema.hasUnevaluated) {
                return true;
            }
            for (Keyword keyword : schema.keywords) {
                keyword.forEachSchema(stack::push);
            }
            SchemaResource resource = schema.resource;
            stack.addAll(resource.dynamicAnchors.values());
            if (resource.recursiveAnchor && resource.rootSchema != null) {
                stack.push(resource.rootSchema);
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface Registration {
        void register(String uri, SchemaResource resource);
    }

    /**
     * Thrown when a reference cannot be resolved; the failure is deferred until the reference is evaluated.
     */
    private static final class UnresolvableReferenceException extends IllegalArgumentException {
        UnresolvableReferenceException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The state shared by the indexing of the subschemas of one schema object.
     *
     * @param document The document
     * @param base The base URI
     * @param vocabularies The vocabularies
     * @param resource The enclosing resource
     * @param registration Where new resources are registered
     */
    private record IndexScope(Document document, String base, Vocabularies vocabularies, SchemaResource resource, Registration registration) {
    }

    /**
     * The schema object whose keywords are being compiled.
     *
     * @param node The schema object
     * @param schema The compiled schema
     * @param info The location of the schema object
     * @param vocabularies The vocabularies of the schema object
     * @param patterns The regular expressions compiled for the schema object
     */
    private record KeywordContext(JsonNode node, Schema schema, NodeInfo info, Vocabularies vocabularies, Map<String, Pattern> patterns) {
        Dialect dialect() {
            return vocabularies.dialect();
        }
    }
}

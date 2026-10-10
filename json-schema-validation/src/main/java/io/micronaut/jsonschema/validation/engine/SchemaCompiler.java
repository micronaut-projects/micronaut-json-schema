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
        boolean documentRoot = resource == null;
        Vocabularies vocab = vocabularies;
        JsonNode schemaKeyword = node.get("$schema");
        if (schemaKeyword != null && schemaKeyword.isString()
            && (documentRoot || (vocab.dialect().atLeast(Dialect.DRAFT_2019_09) && node.get("$id") != null))) {
            vocab = vocabulariesFor(schemaKeyword.getStringValue(), 0);
        }
        Dialect dialect = vocab.dialect();
        String id = null;
        JsonNode idNode = node.get(dialect.idKeyword());
        if (idNode != null && idNode.isString() && !(dialect.refOverridesSiblings() && node.get("$ref") != null)) {
            id = idNode.getStringValue();
        }
        String newBase = base;
        String idAnchor = null;
        boolean newResource = documentRoot;
        if (id != null) {
            String withoutFragment = Uris.stripFragment(id);
            String fragment = Uris.fragment(id);
            if (!withoutFragment.isEmpty()) {
                newBase = Uris.resolve(base, withoutFragment);
                newResource = true;
            }
            if (fragment != null && !fragment.isEmpty() && !dialect.atLeast(Dialect.DRAFT_2019_09)) {
                idAnchor = fragment;
            }
        }
        SchemaResource current = resource;
        String currentPointer = pointer;
        if (newResource || current == null) {
            current = new SchemaResource(newBase, node, vocab, document);
            document.resources.put(newBase, current);
            registration.register(newBase, current);
            currentPointer = "";
        }
        document.nodes.put(node, new NodeInfo(current, currentPointer));
        if (idAnchor != null) {
            current.anchors.put(idAnchor, node);
        }
        if (dialect.atLeast(Dialect.DRAFT_2019_09)) {
            JsonNode anchor = node.get("$anchor");
            if (anchor != null && anchor.isString()) {
                current.anchors.put(anchor.getStringValue(), node);
            }
        }
        if (dialect == Dialect.DRAFT_2020_12) {
            JsonNode dynamicAnchor = node.get("$dynamicAnchor");
            if (dynamicAnchor != null && dynamicAnchor.isString()) {
                current.anchors.putIfAbsent(dynamicAnchor.getStringValue(), node);
                current.dynamicAnchorNodes.put(dynamicAnchor.getStringValue(), node);
            }
        }
        if (dialect == Dialect.DRAFT_2019_09 && currentPointer.isEmpty()) {
            JsonNode recursiveAnchor = node.get("$recursiveAnchor");
            if (recursiveAnchor != null && recursiveAnchor.isBoolean() && recursiveAnchor.getBooleanValue()) {
                current.recursiveAnchor = true;
            }
        }
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            String keyword = entry.getKey();
            JsonNode value = entry.getValue();
            String childPointer = currentPointer + "/" + Uris.escapePointerToken(keyword);
            switch (keyword) {
                case "additionalProperties", "additionalItems", "unevaluatedProperties", "unevaluatedItems", "contains",
                     "propertyNames", "not", "if", "then", "else", "contentSchema" ->
                    index(document, value, newBase, vocab, current, childPointer, registration);
                case "items" -> {
                    if (value.isArray()) {
                        indexArray(document, value, newBase, vocab, current, childPointer, registration);
                    } else {
                        index(document, value, newBase, vocab, current, childPointer, registration);
                    }
                }
                case "allOf", "anyOf", "oneOf", "prefixItems" -> {
                    if (value.isArray()) {
                        indexArray(document, value, newBase, vocab, current, childPointer, registration);
                    }
                }
                case "properties", "patternProperties", "$defs", "definitions", "dependentSchemas", "dependencies" -> {
                    if (value.isObject()) {
                        for (Map.Entry<String, JsonNode> child : value.entries()) {
                            if (!child.getValue().isArray()) {
                                index(document, child.getValue(), newBase, vocab, current,
                                    childPointer + "/" + Uris.escapePointerToken(child.getKey()), registration);
                            }
                        }
                    }
                }
                default -> {
                    // not a subschema location
                }
            }
        }
    }

    private void indexArray(Document document, JsonNode array, String base, Vocabularies vocabularies,
                            SchemaResource resource, String pointer, Registration registration) {
        int i = 0;
        for (JsonNode value : array.values()) {
            index(document, value, base, vocabularies, resource, pointer + "/" + i++, registration);
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
            throw new UnresolvableReferenceException("Unable to resolve reference '" + reference + "' (" + absolute + ") from " + owner.location
                + ": no schema found for " + base, null);
        }
        if (fragment == null || fragment.isEmpty()) {
            return compile(resource.root, new NodeInfo(resource, ""));
        }
        String decoded = Uris.percentDecode(fragment);
        if (decoded.startsWith("/")) {
            JsonNode current = resource.root;
            NodeInfo info = new NodeInfo(resource, "");
            for (String token : Uris.pointerTokens(decoded)) {
                JsonNode next = null;
                if (current.isObject()) {
                    next = current.get(token);
                } else if (current.isArray()) {
                    int index = Uris.parseIndex(token);
                    next = index < 0 ? null : current.get(index);
                }
                if (next == null) {
                    throw new UnresolvableReferenceException("Unable to resolve reference '" + reference + "' (" + absolute + ") from " + owner.location
                        + ": no value at JSON pointer " + decoded, null);
                }
                NodeInfo known = next.isObject() ? resource.document.nodes.get(next) : null;
                info = known != null ? known : new NodeInfo(info.resource(), info.pointer() + "/" + Uris.escapePointerToken(token));
                current = next;
            }
            return compile(current, info);
        }
        JsonNode anchored = resource.anchors.get(decoded);
        if (anchored == null) {
            throw new UnresolvableReferenceException("Unable to resolve reference '" + reference + "' (" + absolute + ") from " + owner.location
                + ": no anchor named '" + decoded + "'", null);
        }
        NodeInfo info = resource.document.nodes.get(anchored);
        return compile(anchored, info != null ? info : new NodeInfo(resource, ""));
    }

    private Keyword reference(Schema owner, String name, String reference) {
        Schema target = null;
        RuntimeException failure = null;
        try {
            target = resolve(owner, reference);
        } catch (UnresolvableReferenceException e) {
            failure = e;
        }
        if (name.equals("$dynamicRef")) {
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
        if (name.equals("$recursiveRef")) {
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
        Dialect dialect = vocab.dialect();
        List<Keyword> keywords = new ArrayList<>(node.size());
        JsonNode ref = node.get("$ref");
        if (dialect.refOverridesSiblings() && ref != null) {
            if (ref.isString()) {
                keywords.add(reference(schema, "$ref", ref.getStringValue()));
            }
            return keywords.toArray(new Keyword[0]);
        }
        List<Keyword> late = new ArrayList<>(0);
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            String keyword = entry.getKey();
            JsonNode value = entry.getValue();
            Keyword compiled = compileCore(keyword, value, schema);
            if (compiled == null && vocab.applicator()) {
                compiled = compileApplicator(keyword, value, node, schema, info, dialect, patterns);
            }
            if (compiled == null && vocab.validation()) {
                compiled = compileValidation(keyword, value, node, schema, dialect);
            }
            if (compiled == null && (engine.assertFormats || vocab.formatAssertion()) && keyword.equals("format") && value.isString()) {
                Predicate<String> validator = Formats.forName(value.getStringValue(), dialect);
                if (validator != null) {
                    compiled = new ValidationKeywords.Format(schema, value.getStringValue(), validator);
                }
            }
            if (compiled == null && dialect == Dialect.DRAFT_7 && (keyword.equals("contentMediaType") || keyword.equals("contentEncoding"))
                && value.isString()) {
                compiled = content(keyword, value.getStringValue(), node, schema);
            }
            if (compiled != null) {
                keywords.add(compiled);
            } else if (unevaluatedEnabled(vocab) && (keyword.equals("unevaluatedProperties") || keyword.equals("unevaluatedItems"))) {
                Schema subschema = sub(value, info, keyword);
                schema.hasUnevaluated = true;
                late.add(keyword.equals("unevaluatedProperties")
                    ? new ApplicatorKeywords.UnevaluatedProperties(schema, subschema)
                    : new ApplicatorKeywords.UnevaluatedItems(schema, subschema));
            }
        }
        if (!late.isEmpty()) {
            late.sort((a, b) -> a.name.compareTo(b.name));
            keywords.addAll(late);
        }
        return keywords.toArray(new Keyword[0]);
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
            case "$ref" -> reference(schema, "$ref", value.getStringValue());
            case "$dynamicRef" -> dialect == Dialect.DRAFT_2020_12 ? reference(schema, "$dynamicRef", value.getStringValue()) : null;
            case "$recursiveRef" -> dialect == Dialect.DRAFT_2019_09 ? reference(schema, "$recursiveRef", value.getStringValue()) : null;
            default -> null;
        };
    }

    private @Nullable Keyword compileApplicator(String keyword, JsonNode value, JsonNode node, Schema schema, NodeInfo info,
                                                Dialect dialect, Map<String, Pattern> patterns) {
        switch (keyword) {
            case "allOf" -> {
                return value.isArray() ? new ApplicatorKeywords.AllOf(schema, subArray(value, info, keyword)) : null;
            }
            case "anyOf" -> {
                return value.isArray() ? new ApplicatorKeywords.AnyOf(schema, subArray(value, info, keyword), value) : null;
            }
            case "oneOf" -> {
                return value.isArray() ? new ApplicatorKeywords.OneOf(schema, subArray(value, info, keyword)) : null;
            }
            case "not" -> {
                return new ApplicatorKeywords.Not(schema, sub(value, info, keyword), value);
            }
            case "if" -> {
                if (!dialect.atLeast(Dialect.DRAFT_7)) {
                    return null;
                }
                JsonNode then = node.get("then");
                JsonNode otherwise = node.get("else");
                return new ApplicatorKeywords.IfThenElse(schema, sub(value, info, keyword),
                    then != null ? sub(then, info, "then") : null,
                    otherwise != null ? sub(otherwise, info, "else") : null);
            }
            case "properties" -> {
                if (!value.isObject()) {
                    return null;
                }
                String[] names = new String[value.size()];
                Schema[] schemas = new Schema[value.size()];
                int i = 0;
                for (Map.Entry<String, JsonNode> property : value.entries()) {
                    names[i] = property.getKey();
                    schemas[i] = sub(property.getValue(), info, keyword, property.getKey());
                    i++;
                }
                return new ApplicatorKeywords.Properties(schema, names, schemas);
            }
            case "patternProperties" -> {
                if (!value.isObject()) {
                    return null;
                }
                Pattern[] compiledPatterns = new Pattern[value.size()];
                Schema[] schemas = new Schema[value.size()];
                int i = 0;
                for (Map.Entry<String, JsonNode> property : value.entries()) {
                    compiledPatterns[i] = pattern(property.getKey(), schema, patterns);
                    schemas[i] = sub(property.getValue(), info, keyword, property.getKey());
                    i++;
                }
                return new ApplicatorKeywords.PatternProperties(schema, compiledPatterns, schemas);
            }
            case "additionalProperties" -> {
                JsonNode properties = node.get("properties");
                Set<String> names = new HashSet<>();
                if (properties != null && properties.isObject()) {
                    for (Map.Entry<String, JsonNode> property : properties.entries()) {
                        names.add(property.getKey());
                    }
                }
                JsonNode patternProperties = node.get("patternProperties");
                List<Pattern> compiledPatterns = new ArrayList<>();
                if (patternProperties != null && patternProperties.isObject()) {
                    for (Map.Entry<String, JsonNode> property : patternProperties.entries()) {
                        compiledPatterns.add(pattern(property.getKey(), schema, patterns));
                    }
                }
                return new ApplicatorKeywords.AdditionalProperties(schema, names, compiledPatterns.toArray(new Pattern[0]), sub(value, info, keyword));
            }
            case "propertyNames" -> {
                return dialect.atLeast(Dialect.DRAFT_6) ? new ApplicatorKeywords.PropertyNames(schema, sub(value, info, keyword)) : null;
            }
            case "dependentSchemas" -> {
                if (!dialect.atLeast(Dialect.DRAFT_2019_09) || !value.isObject()) {
                    return null;
                }
                List<String> names = new ArrayList<>();
                List<Schema> schemas = new ArrayList<>();
                for (Map.Entry<String, JsonNode> dependency : value.entries()) {
                    names.add(dependency.getKey());
                    schemas.add(sub(dependency.getValue(), info, keyword, dependency.getKey()));
                }
                return new ApplicatorKeywords.DependentSchemas(schema, keyword, names.toArray(new String[0]), schemas.toArray(new Schema[0]));
            }
            case "dependencies" -> {
                return value.isObject() ? dependencies(value, schema, info) : null;
            }
            case "items" -> {
                if (dialect == Dialect.DRAFT_2020_12) {
                    if (value.isArray()) {
                        return null;
                    }
                    JsonNode prefixItems = node.get("prefixItems");
                    int start = prefixItems != null && prefixItems.isArray() ? prefixItems.size() : 0;
                    return new ApplicatorKeywords.Items(schema, keyword, start, sub(value, info, keyword));
                }
                if (value.isArray()) {
                    return new ApplicatorKeywords.PrefixItems(schema, keyword, subArray(value, info, keyword));
                }
                return new ApplicatorKeywords.Items(schema, keyword, 0, sub(value, info, keyword));
            }
            case "prefixItems" -> {
                return dialect == Dialect.DRAFT_2020_12 && value.isArray()
                    ? new ApplicatorKeywords.PrefixItems(schema, keyword, subArray(value, info, keyword))
                    : null;
            }
            case "additionalItems" -> {
                if (dialect == Dialect.DRAFT_2020_12) {
                    return null;
                }
                JsonNode items = node.get("items");
                if (items == null || !items.isArray()) {
                    return null;
                }
                return new ApplicatorKeywords.Items(schema, keyword, items.size(), sub(value, info, keyword));
            }
            case "contains" -> {
                if (!dialect.atLeast(Dialect.DRAFT_6)) {
                    return null;
                }
                long min = 1;
                long max = -1;
                boolean explicitMin = false;
                if (dialect.atLeast(Dialect.DRAFT_2019_09)) {
                    JsonNode minContains = node.get("minContains");
                    if (minContains != null && minContains.isNumber()) {
                        min = limit(minContains.getNumberValue());
                        explicitMin = true;
                    }
                    JsonNode maxContains = node.get("maxContains");
                    if (maxContains != null && maxContains.isNumber()) {
                        max = limit(maxContains.getNumberValue());
                    }
                }
                return new ApplicatorKeywords.Contains(schema, sub(value, info, keyword), value, min, explicitMin, max,
                    dialect == Dialect.DRAFT_2020_12);
            }
            default -> {
                return null;
            }
        }
    }

    private Keyword dependencies(JsonNode value, Schema schema, NodeInfo info) {
        Map<String, String[]> required = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        List<Schema> schemas = new ArrayList<>();
        for (Map.Entry<String, JsonNode> dependency : value.entries()) {
            JsonNode dependencyValue = dependency.getValue();
            if (dependencyValue.isArray()) {
                required.put(dependency.getKey(), strings(dependencyValue));
            } else {
                names.add(dependency.getKey());
                schemas.add(sub(dependencyValue, info, "dependencies", dependency.getKey()));
            }
        }
        Keyword schemaDependencies = new ApplicatorKeywords.DependentSchemas(schema, "dependencies", names.toArray(new String[0]), schemas.toArray(new Schema[0]));
        if (required.isEmpty()) {
            return schemaDependencies;
        }
        Keyword requiredDependencies = new ValidationKeywords.DependentRequired(schema, "dependencies", required);
        if (names.isEmpty()) {
            return requiredDependencies;
        }
        return new ApplicatorKeywords.AllOfKeywords(schema, "dependencies", new Keyword[]{requiredDependencies, schemaDependencies});
    }

    private @Nullable Keyword content(String keyword, String value, JsonNode node, Schema schema) {
        JsonNode encoding = node.get("contentEncoding");
        boolean base64 = encoding != null && encoding.isString() && encoding.getStringValue().equalsIgnoreCase("base64");
        if (keyword.equals("contentEncoding")) {
            return base64 ? new ValidationKeywords.ContentEncoding(schema, value) : null;
        }
        String mediaType = value.toLowerCase(Locale.ROOT);
        int semicolon = mediaType.indexOf(';');
        if (semicolon >= 0) {
            mediaType = mediaType.substring(0, semicolon).trim();
        }
        if (mediaType.equals("application/json") || mediaType.endsWith("+json")) {
            return new ValidationKeywords.ContentMediaType(schema, value, base64, engine.jsonMapper);
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

    private static Pattern pattern(String regex, Schema schema, Map<String, Pattern> cache) {
        Pattern pattern = cache.get(regex);
        if (pattern == null) {
            try {
                pattern = EcmaRegex.compile(regex);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid regular expression '" + regex + "' in schema " + schema.location + ": " + e.getMessage(), e);
            }
            cache.put(regex, pattern);
        }
        return pattern;
    }

    private @Nullable Keyword compileValidation(String keyword, JsonNode value, JsonNode node, Schema schema, Dialect dialect) {
        switch (keyword) {
            case "type" -> {
                return type(value, schema, dialect == Dialect.DRAFT_4);
            }
            case "enum" -> {
                return value.isArray() ? new ValidationKeywords.Enum(schema, value) : null;
            }
            case "const" -> {
                return dialect.atLeast(Dialect.DRAFT_6) ? new ValidationKeywords.Const(schema, value) : null;
            }
            case "multipleOf" -> {
                return value.isNumber() ? new ValidationKeywords.MultipleOf(schema, value.getNumberValue()) : null;
            }
            case "maximum", "minimum" -> {
                if (!value.isNumber()) {
                    return null;
                }
                boolean exclusive = false;
                if (dialect == Dialect.DRAFT_4) {
                    JsonNode flag = node.get(keyword.equals("maximum") ? "exclusiveMaximum" : "exclusiveMinimum");
                    exclusive = flag != null && flag.isBoolean() && flag.getBooleanValue();
                }
                return new ValidationKeywords.Bound(schema, keyword, value.getNumberValue(), keyword.equals("maximum"), exclusive);
            }
            case "exclusiveMaximum", "exclusiveMinimum" -> {
                return value.isNumber() && dialect.atLeast(Dialect.DRAFT_6)
                    ? new ValidationKeywords.Bound(schema, keyword, value.getNumberValue(), keyword.equals("exclusiveMaximum"), true)
                    : null;
            }
            case "maxLength", "minLength" -> {
                return value.isNumber() ? new ValidationKeywords.Length(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxLength")) : null;
            }
            case "pattern" -> {
                return value.isString()
                    ? new ValidationKeywords.PatternKeyword(schema, pattern(value.getStringValue(), schema, new HashMap<>()), value.getStringValue())
                    : null;
            }
            case "maxItems", "minItems" -> {
                return value.isNumber() ? new ValidationKeywords.ItemCount(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxItems")) : null;
            }
            case "uniqueItems" -> {
                return value.isBoolean() && value.getBooleanValue() ? new ValidationKeywords.UniqueItems(schema) : null;
            }
            case "maxProperties", "minProperties" -> {
                return value.isNumber()
                    ? new ValidationKeywords.PropertyCount(schema, keyword, limit(value.getNumberValue()), keyword.equals("maxProperties"))
                    : null;
            }
            case "required" -> {
                return value.isArray() ? new ValidationKeywords.Required(schema, strings(value)) : null;
            }
            case "dependentRequired" -> {
                if (!dialect.atLeast(Dialect.DRAFT_2019_09) || !value.isObject()) {
                    return null;
                }
                Map<String, String[]> dependencies = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> dependency : value.entries()) {
                    if (dependency.getValue().isArray()) {
                        dependencies.put(dependency.getKey(), strings(dependency.getValue()));
                    }
                }
                return new ValidationKeywords.DependentRequired(schema, keyword, dependencies);
            }
            default -> {
                return null;
            }
        }
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
}

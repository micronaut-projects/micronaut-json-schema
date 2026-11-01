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
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A schema resource: a schema identified by a base URI (a document root or an embedded {@code $id}).
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class SchemaResource {
    final String uri;
    final JsonNode root;
    final Vocabularies vocabularies;
    final Document document;
    final Map<String, JsonNode> anchors = new HashMap<>();
    final Map<String, JsonNode> dynamicAnchorNodes = new HashMap<>();
    boolean recursiveAnchor;
    @Nullable Schema rootSchema;
    final Map<String, Schema> dynamicAnchors = new HashMap<>();

    SchemaResource(String uri, JsonNode root, Vocabularies vocabularies, Document document) {
        this.uri = uri;
        this.root = root;
        this.vocabularies = vocabularies;
        this.document = document;
    }

    Dialect dialect() {
        return vocabularies.dialect();
    }

    @Nullable Schema dynamicAnchor(String name) {
        return dynamicAnchors.isEmpty() ? null : dynamicAnchors.get(name);
    }

    @Override
    public String toString() {
        return uri;
    }

    /**
     * Location information for an indexed subschema.
     *
     * @param resource The resource the subschema belongs to
     * @param pointer The JSON pointer of the subschema relative to the resource root
     */
    record NodeInfo(SchemaResource resource, String pointer) {
    }

    /**
     * A parsed JSON document containing one or more schema resources.
     */
    static final class Document {
        final String uri;
        final JsonNode root;
        final Map<JsonNode, NodeInfo> nodes = new IdentityHashMap<>();
        final Map<JsonNode, Schema> compiled = new IdentityHashMap<>();
        final Map<String, SchemaResource> resources = new HashMap<>();

        Document(String uri, JsonNode root) {
            this.uri = uri;
            this.root = root;
        }

        @Override
        public String toString() {
            return uri;
        }
    }
}

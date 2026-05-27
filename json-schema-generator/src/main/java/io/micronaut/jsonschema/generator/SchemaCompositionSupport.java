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
package io.micronaut.jsonschema.generator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.model.Schema;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared checks for JSON Schema composition that changes Java type shape.
 *
 * @since 2.0.0
 */
@Internal
public final class SchemaCompositionSupport {

    private static final String DEFINITIONS_REF_PREFIX = "#/definitions/";

    private SchemaCompositionSupport() {
    }

    /**
     * Normalize local references that must be flattened before generation.
     *
     * @param schema The schema to normalize
     */
    public static void normalizeLocalReferences(Schema schema) {
        Set<Schema> path = Collections.newSetFromMap(new IdentityHashMap<>());
        normalizeDefinitions(schema, schema, path);
        flattenLocalAllOfReferences(schema, schema, path);
        flattenRootReference(schema, schema, path);
    }

    /**
     * Check whether a schema has unsupported or ambiguous {@code allOf} composition.
     *
     * @param schema The schema
     * @return True when {@code allOf} cannot be flattened deterministically
     */
    public static boolean hasUnsupportedAllOf(Schema schema) {
        if (schema == null || !schema.hasAllOf()) {
            return false;
        }
        Map<String, Schema> properties = new LinkedHashMap<>();
        Schema additionalProperties = null;
        for (Schema branch : schema.getAllOf()) {
            if (branch == null) {
                continue;
            }
            if (branch.hasOneOf() || branch.hasAnyOf()) {
                return true;
            }
            if (branch.hasAllOf() && hasUnsupportedAllOf(branch)) {
                return true;
            }
            if (hasUnsupportedBranchType(branch)) {
                return true;
            }
            if (branch.has$ref()) {
                return true;
            }
            if (branch.hasProperties()) {
                for (Map.Entry<String, Schema> property : branch.getProperties().entrySet()) {
                    Schema previous = properties.putIfAbsent(property.getKey(), property.getValue());
                    if (previous != null && !isCompatibleProperty(previous, property.getValue())) {
                        return true;
                    }
                }
            }
            if (branch.getAdditionalProperties() != null) {
                if (additionalProperties != null && !isCompatibleAdditionalProperties(additionalProperties, branch.getAdditionalProperties())) {
                    return true;
                }
                additionalProperties = branch.getAdditionalProperties();
            }
        }
        return false;
    }

    /**
     * Check whether a reference points outside the current schema document.
     *
     * @param ref The reference value
     * @return True for external references
     */
    public static boolean isExternalRef(String ref) {
        return ref != null && !isLocalRef(ref);
    }

    /**
     * Check whether a reference points to a supported local target.
     *
     * @param ref The reference value
     * @return True for supported local references
     */
    public static boolean isSupportedLocalRef(String ref) {
        return Schema.THIS_SCHEMA_REF.equals(ref) || localDefinitionName(ref) != null;
    }

    private static void flattenLocalAllOfReferences(Schema schema, Schema documentRoot, Set<Schema> path) {
        if (schema == null || !path.add(schema)) {
            return;
        }
        try {
            if (schema.hasAllOf()) {
                boolean hasResolvedRefBranch = false;
                for (Schema branch : schema.getAllOf()) {
                    if (branch == null) {
                        continue;
                    }
                    Schema referenced = resolveLocalDefinition(documentRoot, branch.get$ref());
                    if (referenced != null && !path.contains(referenced)) {
                        flattenLocalAllOfReferences(referenced, documentRoot, path);
                        if (flattenRootReference(referenced, documentRoot, path)) {
                            branch.set$ref(null);
                            branch.merge(referenced);
                            mergeResolvedAllOfBranch(schema, referenced);
                            hasResolvedRefBranch = true;
                        }
                    }
                    flattenLocalAllOfReferences(branch, documentRoot, path);
                }
                if (hasResolvedRefBranch && resolveLocalDefinition(documentRoot, schema.get$ref()) != null) {
                    schema.set$ref(null);
                }
            }
            if (schema.hasProperties()) {
                schema.getProperties().values().forEach(property -> flattenLocalAllOfReferences(property, documentRoot, path));
            }
            if (schema.getItems() != null) {
                flattenLocalAllOfReferences(schema.getItems(), documentRoot, path);
            }
            if (schema.getContains() != null) {
                flattenLocalAllOfReferences(schema.getContains(), documentRoot, path);
            }
            if (schema.getAdditionalProperties() != null) {
                flattenLocalAllOfReferences(schema.getAdditionalProperties(), documentRoot, path);
            }
        } finally {
            path.remove(schema);
        }
    }

    private static boolean flattenRootReference(Schema schema, Schema documentRoot, Set<Schema> path) {
        Schema referenced = resolveLocalDefinition(documentRoot, schema.get$ref());
        if (referenced == null) {
            return true;
        }
        if (referenced == schema || path.contains(schema) || path.contains(referenced)) {
            return false;
        }
        path.add(schema);
        try {
            flattenLocalAllOfReferences(referenced, documentRoot, path);
            if (!flattenRootReference(referenced, documentRoot, path)) {
                return false;
            }
            String title = schema.getTitle();
            boolean hasTitle = schema.hasTitle();
            schema.set$ref(null);
            schema.merge(referenced);
            if (hasTitle) {
                schema.setTitle(title);
            }
            return true;
        } finally {
            path.remove(schema);
        }
    }

    private static void normalizeDefinitions(Schema schema, Schema documentRoot, Set<Schema> path) {
        if (schema == null || !schema.has$defs()) {
            return;
        }
        schema.get$defs().values().forEach(definition -> {
            flattenLocalAllOfReferences(definition, documentRoot, path);
            flattenRootReference(definition, documentRoot, path);
        });
    }

    private static void mergeResolvedAllOfBranch(Schema schema, Schema branch) {
        String title = schema.getTitle();
        boolean hasTitle = schema.hasTitle();
        schema.merge(branch);
        if (hasTitle) {
            schema.setTitle(title);
        }
    }

    private static Schema resolveLocalDefinition(Schema documentRoot, String ref) {
        String definitionName = localDefinitionName(ref);
        if (definitionName == null || documentRoot == null || !documentRoot.has$defs()) {
            return null;
        }
        return documentRoot.get$defs().get(definitionName);
    }

    private static String localDefinitionName(String ref) {
        if (ref == null) {
            return null;
        }
        if (ref.startsWith(Schema.DEF_SCHEMA_REF_PREFIX)) {
            return ref.substring(Schema.DEF_SCHEMA_REF_PREFIX.length());
        }
        if (ref.startsWith(DEFINITIONS_REF_PREFIX)) {
            return ref.substring(DEFINITIONS_REF_PREFIX.length());
        }
        return null;
    }

    private static boolean isLocalRef(String ref) {
        return ref != null && ref.startsWith("#");
    }

    private static boolean hasUnsupportedBranchType(Schema branch) {
        if (!branch.hasType()) {
            return false;
        }
        var nonNullTypes = branch.getType().stream()
            .filter(type -> !Schema.Type.NULL.equals(type))
            .distinct()
            .toList();
        return nonNullTypes.size() > 1
            || nonNullTypes.stream().anyMatch(type -> !Schema.Type.OBJECT.equals(type));
    }

    private static boolean isCompatibleProperty(Schema first, Schema second) {
        if (first == null || second == null) {
            return true;
        }
        if (first.hasOneOf() || first.hasAnyOf() || second.hasOneOf() || second.hasAnyOf()) {
            return false;
        }
        if ((first.hasAllOf() && hasUnsupportedAllOf(first)) || (second.hasAllOf() && hasUnsupportedAllOf(second))) {
            return false;
        }
        if (first.has$ref() || second.has$ref()) {
            return Objects.equals(first.get$ref(), second.get$ref());
        }
        if (first.hasType() && second.hasType()) {
            var firstTypes = nonNullTypeSignature(first);
            var secondTypes = nonNullTypeSignature(second);
            if (firstTypes.size() > 1 || secondTypes.size() > 1) {
                return false;
            }
            return firstTypes.isEmpty() || secondTypes.isEmpty() || Objects.equals(firstTypes, secondTypes);
        }
        return true;
    }

    private static boolean isCompatibleAdditionalProperties(Schema first, Schema second) {
        if (Schema.FALSE.equals(first) || Schema.FALSE.equals(second)) {
            return Schema.FALSE.equals(first) && Schema.FALSE.equals(second);
        }
        if (Schema.TRUE.equals(first) || Schema.TRUE.equals(second)) {
            return true;
        }
        return isCompatibleProperty(first, second);
    }

    private static List<Schema.Type> nonNullTypeSignature(Schema schema) {
        return schema.getType().stream()
            .filter(type -> !Schema.Type.NULL.equals(type))
            .distinct()
            .collect(Collectors.toList());
    }
}

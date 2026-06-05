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
 * Shared support for same-document JSON Schema references and composition that changes Java type shape.
 *
 * @since 2.0.0
 */
@Internal
public final class SchemaReferenceCompositionSupport {

    private static final String DEFINITIONS_REF_PREFIX = "#/definitions/";

    private SchemaReferenceCompositionSupport() {
    }

    /**
     * Prepare a schema tree for the record-generation path by materializing supported local
     * references before the schema is handed to {@link SourceGenerator}.
     *
     * <p>This method mutates the supplied schema. It resolves same-document definition
     * references ({@code #/$defs/...} and {@code #/definitions/...}) when they appear as root
     * references or inside compatible {@code allOf} branches, then flattens object-compatible
     * {@code allOf} branches into the composed schema. Definitions are prepared as well because
     * they can be emitted as generated Java types.</p>
     *
     * <p>This is deliberately not a general JSON Schema resolver. It does not fetch remote
     * references, resolve arbitrary JSON Pointers, canonicalize schemas, or sort Java members.
     * Unsupported or ambiguous composition is left detectable by {@link #hasUnsupportedAllOf(Schema)}
     * so the record-generation pipeline can fail, skip, or fall back according to context.</p>
     *
     * @param schema The schema to prepare
     */
    public static void prepareLocalCompositionReferences(Schema schema) {
        // Track by instance identity because schemas can be mutable and structurally equal while
        // still representing different nodes.
        Set<Schema> path = Collections.newSetFromMap(new IdentityHashMap<>());
        prepareDefinitions(schema, schema, path);
        flattenLocalAllOfReferences(schema, schema, path);
        flattenRootReference(schema, schema, path);
    }

    /**
     * Check whether a schema has unsupported or ambiguous {@code allOf} composition after
     * supported local references have been prepared.
     *
     * <p>The check accepts only object-compatible branches that can be represented as one Java
     * type. It rejects unresolved references, union-style branches, non-object typed branches,
     * incompatible duplicate properties, and contradictory {@code additionalProperties} rules.</p>
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
                // Union-style branches cannot be flattened into one deterministic object shape.
                return true;
            }
            if (branch.hasAllOf() && hasUnsupportedAllOf(branch)) {
                // Nested allOf is acceptable only if the nested composition is also flattenable.
                return true;
            }
            if (hasUnsupportedBranchType(branch)) {
                // allOf flattening is limited to object-compatible branches.
                return true;
            }
            if (branch.has$ref()) {
                // Local refs should have been resolved before this check; unresolved refs are ambiguous here.
                return true;
            }
            if (branch.hasProperties()) {
                for (Map.Entry<String, Schema> property : branch.getProperties().entrySet()) {
                    Schema previous = properties.putIfAbsent(property.getKey(), property.getValue());
                    if (previous != null && !isCompatibleProperty(previous, property.getValue())) {
                        // The same property may appear in multiple branches only when the schemas agree.
                        return true;
                    }
                }
            }
            if (branch.getAdditionalProperties() != null) {
                if (additionalProperties != null && !isCompatibleAdditionalProperties(additionalProperties, branch.getAdditionalProperties())) {
                    // Contradictory open/closed object policies cannot be represented by one generated type.
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
     * <p>External references are not resolved by this support class or by the record-generation
     * pipeline.</p>
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
     * <p>The supported local targets are the current document ({@code #}) and definitions under
     * {@code #/$defs/...} or legacy {@code #/definitions/...}. Other same-document JSON Pointers
     * are intentionally unsupported here.</p>
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
                            // Replace supported local ref branches with their resolved shape so
                            // allOf can be checked as a single object.
                            branch.set$ref(null);
                            mergeForComposition(branch, referenced);
                            hasResolvedRefBranch = true;
                        }
                    }
                    flattenLocalAllOfReferences(branch, documentRoot, path);
                }
                mergeResolvedAllOfBranches(schema);
                if (hasResolvedRefBranch && resolveLocalDefinition(documentRoot, schema.get$ref()) != null) {
                    // Once all local allOf refs have been materialized, keeping the wrapper ref
                    // would regenerate the same shape twice.
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
            // A referenced definition may itself be a ref or contain allOf refs, so prepare the
            // target shape before merging it here.
            flattenLocalAllOfReferences(referenced, documentRoot, path);
            if (!flattenRootReference(referenced, documentRoot, path)) {
                return false;
            }
            String title = schema.getTitle();
            boolean hasTitle = schema.hasTitle();
            schema.set$ref(null);
            mergeForComposition(schema, referenced);
            if (hasTitle) {
                // Local schema names should keep winning over referenced titles; they drive generated type names.
                schema.setTitle(title);
            }
            return true;
        } finally {
            path.remove(schema);
        }
    }

    private static void prepareDefinitions(Schema schema, Schema documentRoot, Set<Schema> path) {
        if (schema == null || !schema.has$defs()) {
            return;
        }
        schema.get$defs().values().forEach(definition -> {
            // Definitions are generation candidates too, so prepare their internal refs before
            // later compatibility checks.
            flattenLocalAllOfReferences(definition, documentRoot, path);
            flattenRootReference(definition, documentRoot, path);
        });
    }

    private static void mergeResolvedAllOfBranches(Schema schema) {
        for (Schema branch : List.copyOf(schema.getAllOf())) {
            if (branch != null && !branch.has$ref()) {
                mergeResolvedAllOfBranch(schema, branch);
            }
        }
    }

    private static void mergeResolvedAllOfBranch(Schema schema, Schema branch) {
        String title = schema.getTitle();
        boolean hasTitle = schema.hasTitle();
        mergeForComposition(schema, branch);
        if (hasTitle) {
            // Merged allOf branches contribute structure, but should not rename the composed schema.
            schema.setTitle(title);
        }
    }

    private static void mergeForComposition(Schema target, Schema source) {
        Map<String, Schema> mergedProperties = mergeDuplicateProperties(target, source);
        target.merge(source);
        mergedProperties.forEach(target::putProperty);
    }

    private static Map<String, Schema> mergeDuplicateProperties(Schema target, Schema source) {
        Map<String, Schema> targetProperties = target.getProperties();
        Map<String, Schema> sourceProperties = source.getProperties();
        if (targetProperties == null || sourceProperties == null) {
            return Map.of();
        }
        Map<String, Schema> mergedProperties = new LinkedHashMap<>();
        sourceProperties.forEach((name, sourceProperty) -> {
            Schema targetProperty = targetProperties.get(name);
            if (targetProperty != null) {
                mergeForComposition(targetProperty, sourceProperty);
                mergedProperties.put(name, targetProperty);
            }
        });
        return mergedProperties;
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
        // allOf can be flattened only when every typed branch still describes exactly one object shape.
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
            // Different refs may still resolve to compatible shapes, but that requires resolver-aware equivalence.
            return Objects.equals(first.get$ref(), second.get$ref());
        }
        if (first.hasType() && second.hasType()) {
            var firstTypes = nonNullTypeSignature(first);
            var secondTypes = nonNullTypeSignature(second);
            if (firstTypes.size() > 1 || secondTypes.size() > 1) {
                // Multi-type properties need union semantics that the record generator does not model.
                return false;
            }
            return firstTypes.isEmpty() || secondTypes.isEmpty() || Objects.equals(firstTypes, secondTypes);
        }
        return true;
    }

    private static boolean isCompatibleAdditionalProperties(Schema first, Schema second) {
        if (Schema.FALSE.equals(first) || Schema.FALSE.equals(second)) {
            // A closed object policy must agree exactly; otherwise one branch permits fields the other rejects.
            return Schema.FALSE.equals(first) && Schema.FALSE.equals(second);
        }
        if (Schema.TRUE.equals(first) || Schema.TRUE.equals(second)) {
            // The permissive schema does not add a constraint, so the other branch can define the effective value type.
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

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
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.jsonschema.validation.ValidationMessageAdapter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mutable state of a single validation: the error sink, the current instance location,
 * the dynamic scope and the reference stack used to detect infinite recursion.
 * When {@link #errors} is {@code null} the evaluation only needs a boolean result and
 * keywords may stop at the first failure.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class EvaluationContext {
    private static final int REF_CHECK_DEPTH = 32;
    private static final int MAX_REF_DEPTH = 2048;

    @Nullable List<ValidationMessage> errors;
    final boolean collectAnnotations;

    private String[] names = new String[16];
    private int[] indices = new int[16];
    private int depth;

    private SchemaResource[] scope = new SchemaResource[8];
    private int scopeDepth;

    private Object[] refStack = new Object[16];
    private int refDepth;

    EvaluationContext(@Nullable List<ValidationMessage> errors, boolean collectAnnotations) {
        this.errors = errors;
        this.collectAnnotations = collectAnnotations;
    }

    boolean failFast() {
        return errors == null;
    }

    @Nullable Annotations newAnnotations() {
        return collectAnnotations ? new Annotations() : null;
    }

    // ---- error collection ----

    void error(Keyword keyword, String message) {
        List<ValidationMessage> sink = errors;
        if (sink != null) {
            sink.add(new ValidationMessageAdapter(instanceLocation(), keyword.name, keyword.location, message));
        }
    }

    void error(Schema schema, String keyword, String message) {
        List<ValidationMessage> sink = errors;
        if (sink != null) {
            sink.add(new ValidationMessageAdapter(instanceLocation(), keyword, schema.location, message));
        }
    }

    /**
     * Switches the error sink.
     *
     * @param collect Whether the new sink collects errors
     * @return The previous sink
     */
    @Nullable List<ValidationMessage> swapErrors(boolean collect) {
        List<ValidationMessage> previous = errors;
        errors = collect ? new ArrayList<>(4) : null;
        return previous;
    }

    void restoreErrors(@Nullable List<ValidationMessage> previous) {
        errors = previous;
    }

    // ---- instance location ----

    void pushProperty(String name) {
        ensureLocationCapacity();
        names[depth++] = name;
    }

    void pushIndex(int index) {
        ensureLocationCapacity();
        names[depth] = null;
        indices[depth++] = index;
    }

    void pop() {
        depth--;
    }

    private void ensureLocationCapacity() {
        if (depth == names.length) {
            names = Arrays.copyOf(names, depth * 2);
            indices = Arrays.copyOf(indices, depth * 2);
        }
    }

    /**
     * @return The JSON pointer of the current instance location
     */
    String instanceLocation() {
        if (depth == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append('/');
            String name = names[i];
            if (name != null) {
                sb.append(Uris.escapePointerToken(name));
            } else {
                sb.append(indices[i]);
            }
        }
        return sb.toString();
    }

    /**
     * @return The last segment of the instance location
     */
    String lastSegment() {
        if (depth == 0) {
            return "";
        }
        String name = names[depth - 1];
        return name != null ? name : Integer.toString(indices[depth - 1]);
    }

    // ---- dynamic scope ----

    boolean enterResource(SchemaResource resource) {
        if (scopeDepth > 0 && scope[scopeDepth - 1] == resource) {
            return false;
        }
        if (scopeDepth == scope.length) {
            scope = Arrays.copyOf(scope, Math.max(8, scopeDepth * 2));
        }
        scope[scopeDepth++] = resource;
        return true;
    }

    void exitResource() {
        scope[--scopeDepth] = null;
    }

    /**
     * Finds the outermost schema in the dynamic scope declaring the given dynamic anchor.
     *
     * @param anchor The anchor name
     * @return The schema or null
     */
    @Nullable Schema findDynamicAnchor(String anchor) {
        for (int i = 0; i < scopeDepth; i++) {
            Schema schema = scope[i].dynamicAnchor(anchor);
            if (schema != null) {
                return schema;
            }
        }
        return null;
    }

    /**
     * Finds the outermost resource root in the dynamic scope with {@code $recursiveAnchor: true}.
     *
     * @return The schema or null
     */
    @Nullable Schema findRecursiveAnchor() {
        for (int i = 0; i < scopeDepth; i++) {
            SchemaResource resource = scope[i];
            if (resource.recursiveAnchor && resource.rootSchema != null) {
                return resource.rootSchema;
            }
        }
        return null;
    }

    // ---- reference recursion ----

    void enterRef(Schema target, JsonNode instance) {
        int pairs = refDepth / 2;
        if (pairs >= REF_CHECK_DEPTH) {
            if (pairs >= MAX_REF_DEPTH) {
                throw new IllegalStateException("Maximum schema reference depth exceeded evaluating " + target.location);
            }
            for (int i = 0; i < refDepth; i += 2) {
                if (refStack[i] == target && refStack[i + 1] == instance) {
                    throw new IllegalStateException("Infinite recursion detected evaluating " + target.location
                        + " at instance location '" + instanceLocation() + "'");
                }
            }
        }
        if (refDepth + 2 > refStack.length) {
            refStack = Arrays.copyOf(refStack, refStack.length * 2);
        }
        refStack[refDepth++] = target;
        refStack[refDepth++] = instance;
    }

    void exitRef() {
        refStack[--refDepth] = null;
        refStack[--refDepth] = null;
    }
}

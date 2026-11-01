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

/**
 * A compiled schema (a boolean schema or a schema object with its keywords).
 * Compiled schemas are immutable once compilation finished and can be shared between threads.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class Schema {
    private static final Keyword[] NO_KEYWORDS = new Keyword[0];

    final SchemaResource resource;
    final String location;
    final @Nullable Boolean booleanValue;
    Keyword[] keywords = NO_KEYWORDS;
    boolean hasUnevaluated;

    Schema(SchemaResource resource, String location, @Nullable Boolean booleanValue) {
        this.resource = resource;
        this.location = location;
        this.booleanValue = booleanValue;
    }

    boolean isFalse() {
        return Boolean.FALSE.equals(booleanValue);
    }

    /**
     * Evaluates the schema against the instance.
     *
     * @param instance The instance
     * @param ctx The context
     * @param annotations The annotations of the current instance location, null if not collected
     * @return true if valid
     */
    boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
        if (booleanValue != null) {
            if (booleanValue) {
                return true;
            }
            ctx.error(this, "false", "schema for '" + ctx.lastSegment() + "' is false");
            return false;
        }
        Keyword[] kws = keywords;
        if (kws.length == 0) {
            return true;
        }
        boolean entered = ctx.enterResource(resource);
        boolean valid = true;
        for (Keyword keyword : kws) {
            if (!keyword.evaluate(instance, ctx, annotations)) {
                valid = false;
                if (ctx.failFast()) {
                    break;
                }
            }
        }
        if (entered) {
            ctx.exitResource();
        }
        return valid;
    }

    /**
     * Evaluates a child instance (property value or array item).
     *
     * @param instance The child instance
     * @param ctx The context
     * @return true if valid
     */
    boolean evaluateChild(JsonNode instance, EvaluationContext ctx) {
        return evaluate(instance, ctx, ctx.newAnnotations());
    }

    /**
     * Evaluates the schema in place (same instance location), merging annotations only when valid.
     *
     * @param instance The instance
     * @param ctx The context
     * @param annotations The annotations of the current instance location, null if not collected
     * @return true if valid
     */
    boolean evaluateInPlace(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
        if (annotations == null) {
            return evaluate(instance, ctx, null);
        }
        Annotations local = new Annotations();
        boolean valid = evaluate(instance, ctx, local);
        if (valid) {
            annotations.merge(local);
        }
        return valid;
    }

    @Override
    public String toString() {
        return location;
    }
}

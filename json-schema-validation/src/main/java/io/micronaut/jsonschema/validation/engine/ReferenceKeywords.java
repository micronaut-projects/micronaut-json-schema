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

import java.util.function.Consumer;

/**
 * The reference keywords {@code $ref}, {@code $dynamicRef} and {@code $recursiveRef}.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class ReferenceKeywords {

    private ReferenceKeywords() {
    }

    /**
     * A reference keyword whose target was resolved at compile time.
     * Unresolvable references fail when they are evaluated.
     */
    static class Ref extends Keyword {
        final String reference;
        final @Nullable Schema target;
        final @Nullable RuntimeException failure;

        Ref(Schema owner, String name, String reference, @Nullable Schema target, @Nullable RuntimeException failure) {
            super(owner, name);
            this.reference = reference;
            this.target = target;
            this.failure = failure;
        }

        Schema target(EvaluationContext ctx) {
            Schema t = target;
            if (t == null) {
                RuntimeException e = failure;
                // a new exception per evaluation: the stored one is shared by all threads using the compiled schema
                throw new IllegalArgumentException(e != null ? e.getMessage() : "Unable to resolve reference " + reference + " at " + location, e);
            }
            return t;
        }

        @Override
        final boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            Schema t = target(ctx);
            ctx.enterRef(t, instance);
            boolean valid = t.evaluateInPlace(instance, ctx, annotations);
            ctx.exitRef();
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            if (target != null) {
                consumer.accept(target);
            }
        }
    }

    /**
     * The {@code $dynamicRef} keyword.
     */
    static final class DynamicRef extends Ref {
        private final @Nullable String anchor;

        DynamicRef(Schema owner, String reference, @Nullable Schema target, @Nullable RuntimeException failure, @Nullable String anchor) {
            super(owner, "$dynamicRef", reference, target, failure);
            this.anchor = anchor;
        }

        @Override
        Schema target(EvaluationContext ctx) {
            Schema initial = super.target(ctx);
            if (anchor != null) {
                Schema dynamic = ctx.findDynamicAnchor(anchor);
                if (dynamic != null) {
                    return dynamic;
                }
            }
            return initial;
        }
    }

    /**
     * The {@code $recursiveRef} keyword.
     */
    static final class RecursiveRef extends Ref {
        private final boolean dynamic;

        RecursiveRef(Schema owner, String reference, @Nullable Schema target, @Nullable RuntimeException failure, boolean dynamic) {
            super(owner, "$recursiveRef", reference, target, failure);
            this.dynamic = dynamic;
        }

        @Override
        Schema target(EvaluationContext ctx) {
            Schema initial = super.target(ctx);
            if (dynamic) {
                Schema outermost = ctx.findRecursiveAnchor();
                if (outermost != null) {
                    return outermost;
                }
            }
            return initial;
        }
    }
}

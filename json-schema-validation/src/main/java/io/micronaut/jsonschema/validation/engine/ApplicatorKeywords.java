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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Keywords of the applicator and unevaluated vocabularies.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class ApplicatorKeywords {

    private static final String PROPERTY = "property '";
    private static final String INDEX = "index '";

    private ApplicatorKeywords() {
    }

    private static boolean matchesAny(Pattern[] patterns, String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates a schema against the value of an object property.
     */
    private static boolean evaluateProperty(Schema schema, String name, JsonNode value, EvaluationContext ctx) {
        ctx.pushProperty(name);
        boolean valid = schema.evaluateChild(value, ctx);
        ctx.pop();
        return valid;
    }

    /**
     * Evaluates a schema against an array item.
     */
    private static boolean evaluateItem(Schema schema, int index, JsonNode value, EvaluationContext ctx) {
        ctx.pushIndex(index);
        boolean valid = schema.evaluateChild(value, ctx);
        ctx.pop();
        return valid;
    }

    /**
     * The {@code allOf} keyword.
     */
    static final class AllOf extends Keyword {
        private final Schema[] schemas;

        AllOf(Schema owner, Schema[] schemas) {
            super(owner, "allOf");
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            boolean valid = true;
            for (Schema schema : schemas) {
                if (!schema.evaluateInPlace(instance, ctx, annotations)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * Evaluates several keywords compiled from a single schema keyword (e.g. draft-07 {@code dependencies}).
     */
    static final class AllOfKeywords extends Keyword {
        private final Keyword[] keywords;

        AllOfKeywords(Schema owner, String name, Keyword[] keywords) {
            super(owner, name);
            this.keywords = keywords;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            boolean valid = true;
            for (Keyword keyword : keywords) {
                if (!keyword.evaluate(instance, ctx, annotations)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Keyword keyword : keywords) {
                keyword.forEachSchema(consumer);
            }
        }
    }

    /**
     * The {@code anyOf} keyword.
     */
    static final class AnyOf extends Keyword {
        private final Schema[] schemas;
        private final JsonNode raw;

        AnyOf(Schema owner, Schema[] schemas, JsonNode raw) {
            super(owner, "anyOf");
            this.schemas = schemas;
            this.raw = raw;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            List<ValidationMessage> sink = ctx.swapErrors(false);
            boolean valid = false;
            for (Schema schema : schemas) {
                if (schema.evaluateInPlace(instance, ctx, annotations)) {
                    valid = true;
                    if (annotations == null) {
                        break;
                    }
                }
            }
            ctx.restoreErrors(sink);
            if (!valid && sink != null) {
                ctx.error(this, "must be valid to any of the schemas " + JsonValues.toJson(raw));
                for (Schema schema : schemas) {
                    schema.evaluate(instance, ctx, ctx.newAnnotations());
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * The {@code oneOf} keyword.
     */
    static final class OneOf extends Keyword {
        private final Schema[] schemas;

        OneOf(Schema owner, Schema[] schemas) {
            super(owner, "oneOf");
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            List<ValidationMessage> sink = ctx.swapErrors(false);
            Matches matches = match(instance, ctx, sink == null);
            ctx.restoreErrors(sink);
            if (matches.count() == 1) {
                Annotations matched = matches.annotations();
                if (annotations != null && matched != null) {
                    annotations.merge(matched);
                }
                return true;
            }
            if (sink != null) {
                reportErrors(instance, ctx, matches);
            }
            return false;
        }

        /**
         * Evaluates the subschemas, stopping at the second match when only a boolean result is needed.
         */
        private Matches match(JsonNode instance, EvaluationContext ctx, boolean failFast) {
            int count = 0;
            Annotations matched = null;
            StringJoiner indexes = new StringJoiner(", ");
            for (int i = 0; i < schemas.length; i++) {
                Annotations local = ctx.newAnnotations();
                if (!schemas[i].evaluate(instance, ctx, local)) {
                    continue;
                }
                count++;
                indexes.add(Integer.toString(i));
                if (count == 1) {
                    matched = local;
                } else if (failFast) {
                    break;
                }
            }
            return new Matches(count, matched, indexes.toString());
        }

        private void reportErrors(JsonNode instance, EvaluationContext ctx, Matches matches) {
            if (matches.count() == 0) {
                ctx.error(this, "must be valid to one and only one schema, but 0 are valid");
                for (Schema schema : schemas) {
                    schema.evaluate(instance, ctx, ctx.newAnnotations());
                }
            } else {
                ctx.error(this, "must be valid to one and only one schema, but " + matches.count() + " are valid with indexes '" + matches.indexes() + "'");
            }
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }

        /**
         * The result of evaluating the subschemas.
         *
         * @param count The number of matching subschemas
         * @param annotations The annotations of the first matching subschema
         * @param indexes The indexes of the matching subschemas
         */
        private record Matches(int count, @Nullable Annotations annotations, String indexes) {
        }
    }

    /**
     * The {@code not} keyword.
     */
    static final class Not extends Keyword {
        private final Schema schema;
        private final JsonNode raw;

        Not(Schema owner, Schema schema, JsonNode raw) {
            super(owner, "not");
            this.schema = schema;
            this.raw = raw;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            List<ValidationMessage> sink = ctx.swapErrors(false);
            boolean matched = schema.evaluate(instance, ctx, ctx.newAnnotations());
            ctx.restoreErrors(sink);
            if (matched) {
                ctx.error(this, "must not be valid to the schema " + JsonValues.toJson(raw));
                return false;
            }
            return true;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code if}, {@code then} and {@code else} keywords.
     */
    static final class IfThenElse extends Keyword {
        private final Schema condition;
        private final @Nullable Schema then;
        private final @Nullable Schema otherwise;

        IfThenElse(Schema owner, Schema condition, @Nullable Schema then, @Nullable Schema otherwise) {
            super(owner, "if");
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            List<ValidationMessage> sink = ctx.swapErrors(false);
            Annotations local = ctx.newAnnotations();
            boolean matched = condition.evaluate(instance, ctx, local);
            ctx.restoreErrors(sink);
            if (matched) {
                if (annotations != null && local != null) {
                    annotations.merge(local);
                }
                return then == null || then.evaluateInPlace(instance, ctx, annotations);
            }
            return otherwise == null || otherwise.evaluateInPlace(instance, ctx, annotations);
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(condition);
            if (then != null) {
                consumer.accept(then);
            }
            if (otherwise != null) {
                consumer.accept(otherwise);
            }
        }
    }

    /**
     * The {@code properties} keyword.
     */
    static final class Properties extends Keyword {
        private final String[] names;
        private final Schema[] schemas;

        Properties(Schema owner, String[] names, Schema[] schemas) {
            super(owner, "properties");
            this.names = names;
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                JsonNode value = instance.get(name);
                if (value == null) {
                    continue;
                }
                if (annotations != null) {
                    annotations.addProperty(name);
                }
                if (!evaluateProperty(schemas[i], name, value, ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * The {@code patternProperties} keyword.
     */
    static final class PatternProperties extends Keyword {
        private final Pattern[] patterns;
        private final Schema[] schemas;

        PatternProperties(Schema owner, Pattern[] patterns, Schema[] schemas) {
            super(owner, "patternProperties");
            this.patterns = patterns;
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (Map.Entry<String, JsonNode> entry : instance.entries()) {
                if (!evaluateMatchingPatterns(entry.getKey(), entry.getValue(), ctx, annotations)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        private boolean evaluateMatchingPatterns(String name, JsonNode value, EvaluationContext ctx, @Nullable Annotations annotations) {
            boolean valid = true;
            for (int i = 0; i < patterns.length; i++) {
                if (!patterns[i].matcher(name).find()) {
                    continue;
                }
                if (annotations != null) {
                    annotations.addProperty(name);
                }
                if (!evaluateProperty(schemas[i], name, value, ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * The {@code additionalProperties} keyword.
     */
    static final class AdditionalProperties extends Keyword {
        private final Set<String> properties;
        private final Pattern[] patterns;
        private final Schema schema;

        AdditionalProperties(Schema owner, Set<String> properties, Pattern[] patterns, Schema schema) {
            super(owner, "additionalProperties");
            this.properties = properties;
            this.patterns = patterns;
            this.schema = schema;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (Map.Entry<String, JsonNode> entry : instance.entries()) {
                String name = entry.getKey();
                if (properties.contains(name) || matchesAny(patterns, name)) {
                    continue;
                }
                if (annotations != null) {
                    annotations.addProperty(name);
                }
                if (!evaluateAdditional(name, entry.getValue(), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        private boolean evaluateAdditional(String name, JsonNode value, EvaluationContext ctx) {
            if (schema.isFalse()) {
                ctx.error(this, PROPERTY + name + "' is not defined in the schema and the schema does not allow additional properties");
                return false;
            }
            return evaluateProperty(schema, name, value, ctx);
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code propertyNames} keyword.
     */
    static final class PropertyNames extends Keyword {
        private final Schema schema;

        PropertyNames(Schema owner, Schema schema) {
            super(owner, "propertyNames");
            this.schema = schema;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (Map.Entry<String, JsonNode> entry : instance.entries()) {
                if (!evaluateName(entry.getKey(), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        private boolean evaluateName(String name, EvaluationContext ctx) {
            JsonNode nameNode = JsonNode.createStringNode(name);
            List<ValidationMessage> sink = ctx.swapErrors(false);
            boolean valid = schema.evaluate(nameNode, ctx, ctx.newAnnotations());
            if (valid || sink == null) {
                ctx.restoreErrors(sink);
                return valid;
            }
            ctx.swapErrors(true);
            schema.evaluate(nameNode, ctx, ctx.newAnnotations());
            List<ValidationMessage> nested = ctx.errors;
            ctx.restoreErrors(sink);
            String detail = nested == null || nested.isEmpty() ? "schema for property names is false" : detail(nested.get(0));
            ctx.error(this, PROPERTY + name + "' name is not valid: " + detail);
            return false;
        }

        private static String detail(ValidationMessage message) {
            return message instanceof ValidationMessageAdapter adapter ? adapter.getDetail() : message.getMessage();
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code dependentSchemas} keyword (and the schema form of draft-07 {@code dependencies}).
     */
    static final class DependentSchemas extends Keyword {
        private final String[] names;
        private final Schema[] schemas;

        DependentSchemas(Schema owner, String name, String[] names, Schema[] schemas) {
            super(owner, name);
            this.names = names;
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (int i = 0; i < names.length; i++) {
                if (instance.get(names[i]) == null) {
                    continue;
                }
                if (!schemas[i].evaluateInPlace(instance, ctx, annotations)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * Applies schemas to a fixed number of leading array items ({@code prefixItems}, or the array form of {@code items}).
     */
    static final class PrefixItems extends Keyword {
        private final Schema[] schemas;

        PrefixItems(Schema owner, String name, Schema[] schemas) {
            super(owner, name);
            this.schemas = schemas;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            int count = Math.min(schemas.length, instance.size());
            if (annotations != null) {
                annotations.evaluateItemsUpTo(count);
            }
            boolean valid = true;
            for (int i = 0; i < count; i++) {
                if (!evaluateItem(schemas[i], i, instance.get(i), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            for (Schema schema : schemas) {
                consumer.accept(schema);
            }
        }
    }

    /**
     * Applies a schema to all array items from a start index ({@code items}, {@code additionalItems}).
     */
    static final class Items extends Keyword {
        private final int start;
        private final Schema schema;

        Items(Schema owner, String name, int start, Schema schema) {
            super(owner, name);
            this.start = start;
            this.schema = schema;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            int size = instance.size();
            if (annotations != null && size > start) {
                annotations.evaluateAllItems();
            }
            boolean valid = true;
            for (int i = start; i < size; i++) {
                if (!evaluateAdditional(i, instance.get(i), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            return valid;
        }

        private boolean evaluateAdditional(int index, JsonNode item, EvaluationContext ctx) {
            if (schema.isFalse()) {
                ctx.error(this, INDEX + index + "' is not defined in the schema and the schema does not allow additional items");
                return false;
            }
            return evaluateItem(schema, index, item, ctx);
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code contains} keyword together with {@code minContains} and {@code maxContains}.
     */
    static final class Contains extends Keyword {
        private final Schema schema;
        private final JsonNode raw;
        private final long min;
        private final long max;
        private final boolean annotate;
        private final boolean explicitMin;

        Contains(Schema owner, Schema schema, JsonNode raw, long min, boolean explicitMin, long max, boolean annotate) {
            super(owner, "contains");
            this.schema = schema;
            this.raw = raw;
            this.min = min;
            this.explicitMin = explicitMin;
            this.max = max;
            this.annotate = annotate;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            List<ValidationMessage> sink = ctx.swapErrors(false);
            long count = countMatches(instance, ctx, annotate ? annotations : null);
            ctx.restoreErrors(sink);
            if (count < min) {
                if (explicitMin) {
                    ctx.error(this, "must contain at least " + min + " element(s) that passes these validations: " + JsonValues.toJson(raw));
                } else {
                    ctx.error(this, "does not contain an element that passes these validations: " + JsonValues.toJson(raw));
                }
                return false;
            }
            if (max >= 0 && count > max) {
                ctx.error(this, "must contain at most " + max + " element(s) that passes these validations: " + JsonValues.toJson(raw));
                return false;
            }
            return true;
        }

        /**
         * Counts the matching items, stopping once the minimum is reached unless all items must be evaluated.
         *
         * @param annotations The annotations to record matching items into, or null
         */
        private long countMatches(JsonNode array, EvaluationContext ctx, @Nullable Annotations annotations) {
            int size = array.size();
            long count = 0;
            boolean needAll = max >= 0 || annotations != null;
            for (int i = 0; i < size && (needAll || count < min); i++) {
                if (evaluateItem(schema, i, array.get(i), ctx)) {
                    count++;
                    if (annotations != null) {
                        annotations.evaluateItem(i);
                    }
                }
            }
            return count;
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code unevaluatedProperties} keyword.
     */
    static final class UnevaluatedProperties extends Keyword {
        private final Schema schema;

        UnevaluatedProperties(Schema owner, Schema schema) {
            super(owner, "unevaluatedProperties");
            this.schema = schema;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (Map.Entry<String, JsonNode> entry : instance.entries()) {
                String name = entry.getKey();
                if (annotations != null && annotations.isPropertyEvaluated(name)) {
                    continue;
                }
                if (!evaluateUnevaluated(name, entry.getValue(), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            if (valid && annotations != null) {
                for (Map.Entry<String, JsonNode> entry : instance.entries()) {
                    annotations.addProperty(entry.getKey());
                }
            }
            return valid;
        }

        private boolean evaluateUnevaluated(String name, JsonNode value, EvaluationContext ctx) {
            if (schema.isFalse()) {
                ctx.error(this, PROPERTY + name + "' is not evaluated and the schema does not allow unevaluated properties");
                return false;
            }
            return evaluateProperty(schema, name, value, ctx);
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }

    /**
     * The {@code unevaluatedItems} keyword.
     */
    static final class UnevaluatedItems extends Keyword {
        private final Schema schema;

        UnevaluatedItems(Schema owner, Schema schema) {
            super(owner, "unevaluatedItems");
            this.schema = schema;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            boolean valid = true;
            int size = instance.size();
            for (int i = 0; i < size; i++) {
                if (annotations != null && annotations.isItemEvaluated(i)) {
                    continue;
                }
                if (!evaluateUnevaluated(i, instance.get(i), ctx)) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                }
            }
            if (valid && annotations != null) {
                annotations.evaluateAllItems();
            }
            return valid;
        }

        private boolean evaluateUnevaluated(int index, JsonNode item, EvaluationContext ctx) {
            if (schema.isFalse()) {
                ctx.error(this, INDEX + index + "' is not evaluated and the schema does not allow unevaluated items");
                return false;
            }
            return evaluateItem(schema, index, item, ctx);
        }

        @Override
        void forEachSchema(Consumer<Schema> consumer) {
            consumer.accept(schema);
        }
    }
}

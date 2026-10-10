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

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Keywords of the validation and format vocabularies.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class ValidationKeywords {

    static final int NULL = 1;
    static final int BOOLEAN = 1 << 1;
    static final int OBJECT = 1 << 2;
    static final int ARRAY = 1 << 3;
    static final int NUMBER = 1 << 4;
    static final int STRING = 1 << 5;
    static final int INTEGER = 1 << 6;

    private ValidationKeywords() {
    }

    static int typeBit(String type) {
        return switch (type) {
            case "null" -> NULL;
            case "boolean" -> BOOLEAN;
            case "object" -> OBJECT;
            case "array" -> ARRAY;
            case "number" -> NUMBER;
            case "string" -> STRING;
            case "integer" -> INTEGER;
            default -> 0;
        };
    }

    static byte @Nullable [] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The {@code type} keyword.
     */
    static final class Type extends Keyword {
        private final int types;
        private final String expected;
        private final boolean strictInteger;

        Type(Schema owner, int types, String expected, boolean strictInteger) {
            super(owner, "type");
            this.types = types;
            this.expected = expected;
            this.strictInteger = strictInteger;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (matches(instance)) {
                return true;
            }
            ctx.error(this, JsonValues.typeName(instance) + " found, " + expected + " expected");
            return false;
        }

        private boolean isInteger(Number n) {
            return strictInteger ? JsonValues.isIntegralType(n) : JsonValues.isInteger(n);
        }

        private boolean matches(JsonNode instance) {
            if (instance.isString()) {
                return (types & STRING) != 0;
            }
            if (instance.isNumber()) {
                return (types & NUMBER) != 0 || ((types & INTEGER) != 0 && isInteger(instance.getNumberValue()));
            }
            if (instance.isObject()) {
                return (types & OBJECT) != 0;
            }
            if (instance.isArray()) {
                return (types & ARRAY) != 0;
            }
            if (instance.isBoolean()) {
                return (types & BOOLEAN) != 0;
            }
            return (types & NULL) != 0;
        }
    }

    /**
     * The {@code enum} keyword.
     */
    static final class Enum extends Keyword {
        private final JsonNode values;

        Enum(Schema owner, JsonNode values) {
            super(owner, "enum");
            this.values = values;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            for (JsonNode value : values.values()) {
                if (JsonValues.equal(value, instance)) {
                    return true;
                }
            }
            ctx.error(this, "does not have a value in the enumeration " + JsonValues.toJson(values));
            return false;
        }
    }

    /**
     * The {@code const} keyword.
     */
    static final class Const extends Keyword {
        private final JsonNode value;

        Const(Schema owner, JsonNode value) {
            super(owner, "const");
            this.value = value;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (JsonValues.equal(value, instance)) {
                return true;
            }
            ctx.error(this, "must be the constant value '" + JsonValues.text(value) + "'");
            return false;
        }
    }

    /**
     * The {@code minimum}, {@code maximum}, {@code exclusiveMinimum} and {@code exclusiveMaximum} keywords.
     */
    static final class Bound extends Keyword {
        private final Number limit;
        private final boolean maximum;
        private final boolean exclusive;
        private final String message;

        Bound(Schema owner, String name, Number limit, boolean maximum, boolean exclusive) {
            super(owner, name);
            this.limit = limit;
            this.maximum = maximum;
            this.exclusive = exclusive;
            this.message = "must have " + (exclusive ? "an exclusive " : "a ") + (maximum ? "maximum" : "minimum") + " value of " + JsonValues.text(limit);
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isNumber()) {
                return true;
            }
            int cmp = JsonValues.compare(instance.getNumberValue(), limit);
            boolean valid;
            if (maximum) {
                valid = exclusive ? cmp < 0 : cmp <= 0;
            } else {
                valid = exclusive ? cmp > 0 : cmp >= 0;
            }
            if (!valid) {
                ctx.error(this, message);
            }
            return valid;
        }
    }

    /**
     * The {@code multipleOf} keyword.
     */
    static final class MultipleOf extends Keyword {
        private final Number divisor;

        MultipleOf(Schema owner, Number divisor) {
            super(owner, "multipleOf");
            this.divisor = divisor;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isNumber() || JsonValues.isMultipleOf(instance.getNumberValue(), divisor)) {
                return true;
            }
            ctx.error(this, "must be multiple of " + JsonValues.text(divisor));
            return false;
        }
    }

    /**
     * The {@code minLength} and {@code maxLength} keywords.
     */
    static final class Length extends Keyword {
        private final long limit;
        private final boolean maximum;

        Length(Schema owner, String name, long limit, boolean maximum) {
            super(owner, name);
            this.limit = limit;
            this.maximum = maximum;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isString()) {
                return true;
            }
            String s = instance.getStringValue();
            int chars = s.length();
            if (maximum) {
                if (chars <= limit || s.codePointCount(0, chars) <= limit) {
                    return true;
                }
                ctx.error(this, "must be at most " + limit + " characters long");
            } else {
                if (chars < limit || s.codePointCount(0, chars) < limit) {
                    ctx.error(this, "must be at least " + limit + " characters long");
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    /**
     * The {@code pattern} keyword.
     */
    static final class PatternKeyword extends Keyword {
        private final Pattern pattern;
        private final String source;

        PatternKeyword(Schema owner, Pattern pattern, String source) {
            super(owner, "pattern");
            this.pattern = pattern;
            this.source = source;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isString() || pattern.matcher(instance.getStringValue()).find()) {
                return true;
            }
            ctx.error(this, "does not match the regex pattern " + source);
            return false;
        }
    }

    /**
     * The {@code format} keyword.
     */
    static final class Format extends Keyword {
        private final String format;
        private final Predicate<String> validator;

        Format(Schema owner, String format, Predicate<String> validator) {
            super(owner, "format");
            this.format = format;
            this.validator = validator;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isString() || validator.test(instance.getStringValue())) {
                return true;
            }
            ctx.error(this, "does not match the " + format + " pattern" + Formats.description(format));
            return false;
        }
    }

    /**
     * The draft-07 {@code contentEncoding} keyword (base64).
     */
    static final class ContentEncoding extends Keyword {
        private final String encoding;

        ContentEncoding(Schema owner, String encoding) {
            super(owner, "contentEncoding");
            this.encoding = encoding;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isString() || decode(instance.getStringValue()) != null) {
                return true;
            }
            ctx.error(this, "does not match content encoding " + encoding);
            return false;
        }
    }

    /**
     * The draft-07 {@code contentMediaType} keyword (JSON media types).
     */
    static final class ContentMediaType extends Keyword {
        private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);

        private final String mediaType;
        private final boolean base64;
        private final JsonMapper jsonMapper;

        ContentMediaType(Schema owner, String mediaType, boolean base64, JsonMapper jsonMapper) {
            super(owner, "contentMediaType");
            this.mediaType = mediaType;
            this.base64 = base64;
            this.jsonMapper = jsonMapper;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isString()) {
                return true;
            }
            byte[] content = base64 ? decode(instance.getStringValue()) : instance.getStringValue().getBytes(StandardCharsets.UTF_8);
            if (content == null) {
                // reported by contentEncoding
                return true;
            }
            try {
                jsonMapper.readValue(content, JSON_NODE);
                return true;
            } catch (IOException | RuntimeException e) {
                ctx.error(this, "is not a content media type " + mediaType);
                return false;
            }
        }
    }

    /**
     * The {@code minItems} and {@code maxItems} keywords.
     */
    static final class ItemCount extends Keyword {
        private final long limit;
        private final boolean maximum;

        ItemCount(Schema owner, String name, long limit, boolean maximum) {
            super(owner, name);
            this.limit = limit;
            this.maximum = maximum;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            int size = instance.size();
            if (maximum ? size <= limit : size >= limit) {
                return true;
            }
            ctx.error(this, "must have " + (maximum ? "at most " : "at least ") + limit + " items but found " + size);
            return false;
        }
    }

    /**
     * The {@code uniqueItems} keyword.
     */
    static final class UniqueItems extends Keyword {
        UniqueItems(Schema owner) {
            super(owner, "uniqueItems");
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isArray()) {
                return true;
            }
            int size = instance.size();
            for (int i = 0; i < size; i++) {
                JsonNode a = instance.get(i);
                for (int j = i + 1; j < size; j++) {
                    if (a != null && JsonValues.equal(a, instance.get(j))) {
                        ctx.error(this, "must have only unique items in the array");
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /**
     * The {@code minProperties} and {@code maxProperties} keywords.
     */
    static final class PropertyCount extends Keyword {
        private final long limit;
        private final boolean maximum;

        PropertyCount(Schema owner, String name, long limit, boolean maximum) {
            super(owner, name);
            this.limit = limit;
            this.maximum = maximum;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            int size = instance.size();
            if (maximum ? size <= limit : size >= limit) {
                return true;
            }
            ctx.error(this, "must have " + (maximum ? "at most " : "at least ") + limit + " properties");
            return false;
        }
    }

    /**
     * The {@code required} keyword.
     */
    static final class Required extends Keyword {
        private final String[] names;

        Required(Schema owner, String[] names) {
            super(owner, "required");
            this.names = names;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (String name : names) {
                if (instance.get(name) == null) {
                    valid = false;
                    if (ctx.failFast()) {
                        return false;
                    }
                    ctx.error(this, "required property '" + name + "' not found");
                }
            }
            return valid;
        }
    }

    /**
     * The {@code dependentRequired} keyword (and the array form of draft-07 {@code dependencies}).
     */
    static final class DependentRequired extends Keyword {
        private final Map<String, String[]> dependencies;

        DependentRequired(Schema owner, String name, Map<String, String[]> dependencies) {
            super(owner, name);
            this.dependencies = dependencies;
        }

        @Override
        boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations) {
            if (!instance.isObject()) {
                return true;
            }
            boolean valid = true;
            for (Map.Entry<String, String[]> entry : dependencies.entrySet()) {
                if (instance.get(entry.getKey()) == null) {
                    continue;
                }
                for (String required : entry.getValue()) {
                    if (instance.get(required) == null) {
                        valid = false;
                        if (ctx.failFast()) {
                            return false;
                        }
                        ctx.error(this, "has a missing property '" + required + "' which is dependent required because '" + entry.getKey() + "' is present");
                    }
                }
            }
            return valid;
        }
    }
}

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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Helpers for comparing and rendering JSON values according to the JSON Schema data model.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class JsonValues {

    private static final long MAX_EXACT_DOUBLE = 1L << 53;

    private JsonValues() {
    }

    /**
     * @param node The node
     * @return The JSON Schema type name of the node
     */
    static String typeName(JsonNode node) {
        if (node.isString()) {
            return "string";
        }
        if (node.isNumber()) {
            Number n = node.getNumberValue();
            return isIntegralType(n) ? "integer" : "number";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return "null";
    }

    /**
     * @param n The number
     * @return Whether the number is stored as an integral Java type
     */
    static boolean isIntegralType(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte || n instanceof BigInteger;
    }

    private static boolean isSmallIntegral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    private static boolean isFloating(Number n) {
        return n instanceof Double || n instanceof Float;
    }

    /**
     * @param n The number
     * @return Whether the number has no fractional part
     */
    static boolean isInteger(Number n) {
        if (isIntegralType(n)) {
            return true;
        }
        if (isFloating(n)) {
            double d = n.doubleValue();
            return !Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d);
        }
        BigDecimal bd = toBigDecimal(n);
        return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
    }

    /**
     * @param n The number
     * @return The number as a big decimal
     */
    static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        }
        if (n instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (n instanceof Float f) {
            return new BigDecimal(Float.toString(f));
        }
        if (n instanceof Double d) {
            return new BigDecimal(Double.toString(d));
        }
        if (isSmallIntegral(n)) {
            return BigDecimal.valueOf(n.longValue());
        }
        return new BigDecimal(n.toString());
    }

    /**
     * Compares two numbers by their mathematical value.
     *
     * @param a The first number
     * @param b The second number
     * @return The comparison result
     */
    static int compare(Number a, Number b) {
        if (isSmallIntegral(a) && isSmallIntegral(b)) {
            return Long.compare(a.longValue(), b.longValue());
        }
        if (isExactDouble(a) && isExactDouble(b)) {
            return compareDoubles(asDouble(a), asDouble(b));
        }
        if (isNonFinite(a)) {
            return a.doubleValue() > 0 ? 1 : -1;
        }
        if (isNonFinite(b)) {
            return b.doubleValue() > 0 ? -1 : 1;
        }
        return toBigDecimal(a).compareTo(toBigDecimal(b));
    }

    /**
     * @param n The number
     * @return Whether the number is a double or float, or an integral value that a double represents exactly
     */
    private static boolean isExactDouble(Number n) {
        return isFloating(n) || (isSmallIntegral(n) && Math.abs(n.longValue()) <= MAX_EXACT_DOUBLE);
    }

    private static boolean isNonFinite(Number n) {
        return isFloating(n) && !Double.isFinite(n.doubleValue());
    }

    /**
     * @param n An integral or floating point number
     * @return The number as a double. A float is converted by its decimal representation, as in {@link #toBigDecimal(Number)},
     * so that {@code 0.1f} equals {@code 0.1}
     */
    private static double asDouble(Number n) {
        return n instanceof Float f ? Double.parseDouble(Float.toString(f)) : n.doubleValue();
    }

    private static int compareDoubles(double a, double b) {
        if (a < b) {
            return -1;
        }
        return a > b ? 1 : 0;
    }

    /**
     * @param value The value
     * @param divisor The divisor
     * @return Whether the value is a multiple of the divisor
     */
    static boolean isMultipleOf(Number value, Number divisor) {
        if (isSmallIntegral(value) && isSmallIntegral(divisor)) {
            long d = divisor.longValue();
            return d != 0 && value.longValue() % d == 0;
        }
        if (isFloating(value) && !Double.isFinite(value.doubleValue())) {
            return false;
        }
        if (isFloating(divisor) && !Double.isFinite(divisor.doubleValue())) {
            // an overflowed divisor: only zero is a multiple
            return compare(value, 0) == 0;
        }
        BigDecimal d = toBigDecimal(divisor).stripTrailingZeros();
        BigDecimal v = toBigDecimal(value).stripTrailingZeros();
        if (d.signum() == 0) {
            return false;
        }
        if (v.signum() == 0) {
            return true;
        }
        // v = a * 10^-scale(v) and d = b * 10^-scale(d), so v / d = (a / b) * 10^k: modular arithmetic keeps
        // values with huge exponents (1e1000000) cheap
        BigInteger a = v.unscaledValue().abs();
        BigInteger b = d.unscaledValue().abs();
        long k = (long) d.scale() - v.scale();
        if (k >= 0) {
            return a.multiply(BigInteger.TEN.modPow(BigInteger.valueOf(k), b)).mod(b).signum() == 0;
        }
        if (-k >= v.precision()) {
            // a < 10^-k, so a is not a multiple of b * 10^-k
            return false;
        }
        return a.mod(b.multiply(BigInteger.TEN.pow((int) -k))).signum() == 0;
    }

    /**
     * @param n The number
     * @return Whether the number is NaN, which JSON cannot represent
     */
    static boolean isNaN(Number n) {
        return isFloating(n) && Double.isNaN(n.doubleValue());
    }

    /**
     * Computes a hash code consistent with {@link #jsonEquals(JsonNode, JsonNode)}.
     *
     * @param node The node
     * @return The hash code
     */
    static int jsonHash(JsonNode node) {
        if (node.isNumber()) {
            return numberHash(node.getNumberValue());
        }
        if (node.isArray()) {
            int hash = 1;
            for (JsonNode value : node.values()) {
                hash = 31 * hash + jsonHash(value);
            }
            return hash;
        }
        if (node.isObject()) {
            int hash = 0;
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                hash += entry.getKey().hashCode() ^ jsonHash(entry.getValue());
            }
            return hash;
        }
        return node.hashCode();
    }

    private static int numberHash(Number n) {
        if (isSmallIntegral(n)) {
            return Long.hashCode(n.longValue());
        }
        if (isFloating(n) && !Double.isFinite(n.doubleValue())) {
            return Double.hashCode(n.doubleValue());
        }
        BigDecimal value = toBigDecimal(n).stripTrailingZeros();
        if (value.signum() == 0) {
            return 0;
        }
        // integral values that fit a long hash like the small integral types
        if (value.scale() <= 0 && value.precision() - value.scale() <= 19) {
            try {
                return Long.hashCode(value.longValueExact());
            } catch (ArithmeticException e) {
                // a 19 digit value outside the long range
            }
        }
        return value.hashCode();
    }

    /**
     * Compares two JSON values for equality as defined by JSON Schema (numbers by mathematical value).
     *
     * @param a The first value
     * @param b The second value
     * @return true if equal
     */
    static boolean jsonEquals(JsonNode a, JsonNode b) {
        if (a == b) {
            return true;
        }
        if (a.isNumber()) {
            return b.isNumber() && !isNaN(a.getNumberValue()) && !isNaN(b.getNumberValue())
                && compare(a.getNumberValue(), b.getNumberValue()) == 0;
        }
        if (a.isString()) {
            return b.isString() && a.getStringValue().equals(b.getStringValue());
        }
        if (a.isBoolean()) {
            return b.isBoolean() && a.getBooleanValue() == b.getBooleanValue();
        }
        if (a.isNull()) {
            return b.isNull();
        }
        if (a.isArray()) {
            return b.isArray() && arrayEquals(a, b);
        }
        return a.isObject() && b.isObject() && objectEquals(a, b);
    }

    private static boolean arrayEquals(JsonNode a, JsonNode b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            JsonNode ai = a.get(i);
            JsonNode bi = b.get(i);
            if (ai == null || bi == null || !jsonEquals(ai, bi)) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectEquals(JsonNode a, JsonNode b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, JsonNode> entry : a.entries()) {
            JsonNode other = b.get(entry.getKey());
            if (other == null || !jsonEquals(entry.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Renders a value for a message: strings are rendered without quotes, anything else as JSON.
     *
     * @param node The node
     * @return The text
     */
    static String text(JsonNode node) {
        if (node.isString()) {
            return node.getStringValue();
        }
        return toJson(node);
    }

    /**
     * @param n The number
     * @return The textual representation of the number
     */
    static String text(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd.toString();
        }
        return n.toString();
    }

    /**
     * @param node The node
     * @return The node rendered as compact JSON
     */
    static String toJson(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    private static void write(JsonNode node, StringBuilder sb) {
        if (node.isString()) {
            writeString(node.getStringValue(), sb);
        } else if (node.isNumber()) {
            sb.append(text(node.getNumberValue()));
        } else if (node.isBoolean()) {
            sb.append(node.getBooleanValue());
        } else if (node.isArray()) {
            writeArray(node, sb);
        } else if (node.isObject()) {
            writeObject(node, sb);
        } else {
            sb.append("null");
        }
    }

    private static void writeArray(JsonNode node, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (JsonNode value : node.values()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(value, sb);
        }
        sb.append(']');
    }

    private static void writeObject(JsonNode node, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            write(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}

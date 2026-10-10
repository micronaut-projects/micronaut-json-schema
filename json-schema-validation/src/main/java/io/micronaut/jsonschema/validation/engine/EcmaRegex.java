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

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles ECMA-262 regular expressions (as used by JSON Schema) to {@link Pattern}s.
 * The translation covers the differences that matter for JSON Schema: {@code $} only
 * matches at the end of input, {@code \s} covers Unicode white space, {@code \v} and
 * {@code \0} have their ECMA-262 meaning, long Unicode property names are mapped and
 * {@code [} and {@code &} are literals inside character classes.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class EcmaRegex {

    private static final String WHITESPACE = "\\t\\n\\x0B\\f\\r \\x{A0}\\x{1680}\\x{2000}-\\x{200A}\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}\\x{FEFF}";
    private static final String ECMA_LETTER_ESCAPES = "bBcdDfknpPrsStuvwWx";
    private static final Map<String, String> CATEGORY_NAMES = Map.ofEntries(
        Map.entry("Letter", "L"),
        Map.entry("Cased_Letter", "LC"),
        Map.entry("Uppercase_Letter", "Lu"),
        Map.entry("Lowercase_Letter", "Ll"),
        Map.entry("Titlecase_Letter", "Lt"),
        Map.entry("Modifier_Letter", "Lm"),
        Map.entry("Other_Letter", "Lo"),
        Map.entry("Mark", "M"),
        Map.entry("Nonspacing_Mark", "Mn"),
        Map.entry("Spacing_Mark", "Mc"),
        Map.entry("Enclosing_Mark", "Me"),
        Map.entry("Number", "N"),
        Map.entry("Decimal_Number", "Nd"),
        Map.entry("digit", "Nd"),
        Map.entry("Letter_Number", "Nl"),
        Map.entry("Other_Number", "No"),
        Map.entry("Punctuation", "P"),
        Map.entry("punct", "P"),
        Map.entry("Connector_Punctuation", "Pc"),
        Map.entry("Dash_Punctuation", "Pd"),
        Map.entry("Open_Punctuation", "Ps"),
        Map.entry("Close_Punctuation", "Pe"),
        Map.entry("Initial_Punctuation", "Pi"),
        Map.entry("Final_Punctuation", "Pf"),
        Map.entry("Other_Punctuation", "Po"),
        Map.entry("Symbol", "S"),
        Map.entry("Math_Symbol", "Sm"),
        Map.entry("Currency_Symbol", "Sc"),
        Map.entry("Modifier_Symbol", "Sk"),
        Map.entry("Other_Symbol", "So"),
        Map.entry("Separator", "Z"),
        Map.entry("Space_Separator", "Zs"),
        Map.entry("Line_Separator", "Zl"),
        Map.entry("Paragraph_Separator", "Zp"),
        Map.entry("Other", "C"),
        Map.entry("Control", "Cc"),
        Map.entry("cntrl", "Cc"),
        Map.entry("Format", "Cf"),
        Map.entry("Surrogate", "Cs"),
        Map.entry("Private_Use", "Co"),
        Map.entry("Unassigned", "Cn")
    );

    private EcmaRegex() {
    }

    /**
     * Compiles the given ECMA-262 regular expression.
     *
     * @param ecmaPattern The pattern
     * @return The compiled pattern
     * @throws PatternSyntaxException If the pattern is invalid
     */
    static Pattern compile(String ecmaPattern) {
        return Pattern.compile(translate(ecmaPattern, false));
    }

    /**
     * Checks whether the given string is a valid ECMA-262 regular expression.
     *
     * @param ecmaPattern The pattern
     * @return true if valid
     */
    static boolean isValid(String ecmaPattern) {
        try {
            Pattern.compile(translate(ecmaPattern, true));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String translate(String p, boolean strict) {
        StringBuilder sb = new StringBuilder(p.length() + 16);
        boolean inClass = false;
        int len = p.length();
        for (int i = 0; i < len; i++) {
            char c = p.charAt(i);
            if (c == '\\') {
                if (i + 1 >= len) {
                    throw new IllegalArgumentException("Trailing backslash in pattern: " + p);
                }
                char n = p.charAt(++i);
                switch (n) {
                    case 's' -> sb.append(inClass ? WHITESPACE : "[" + WHITESPACE + "]");
                    case 'S' -> sb.append(inClass ? "[^" + WHITESPACE + "]" : "[^" + WHITESPACE + "]");
                    case 'v' -> sb.append("\\x0B");
                    case '0' -> {
                        if (i + 1 < len && Character.isDigit(p.charAt(i + 1))) {
                            throw new IllegalArgumentException("Invalid octal escape in pattern: " + p);
                        }
                        sb.append("\\x00");
                    }
                    case 'b' -> sb.append(inClass ? "\\x08" : "\\b");
                    case 'd', 'D', 'w', 'W', 'B', 'f', 'n', 'r', 't', 'k' -> sb.append('\\').append(n);
                    case 'c' -> {
                        if (i + 1 < len && isAsciiLetter(p.charAt(i + 1))) {
                            sb.append(String.format("\\x%02X", p.charAt(++i) % 32));
                        } else if (strict) {
                            throw new IllegalArgumentException("Invalid control escape in pattern: " + p);
                        } else {
                            sb.append("\\\\c");
                        }
                    }
                    case 'x' -> sb.append("\\x");
                    case 'u' -> {
                        if (i + 1 < len && p.charAt(i + 1) == '{') {
                            int close = p.indexOf('}', i);
                            if (close < 0) {
                                throw new IllegalArgumentException("Invalid unicode escape in pattern: " + p);
                            }
                            sb.append("\\x{").append(p, i + 2, close).append('}');
                            i = close;
                        } else {
                            sb.append("\\u");
                        }
                    }
                    case 'p', 'P' -> i = appendProperty(p, i, n, sb);
                    default -> {
                        if (isAsciiLetter(n)) {
                            if (strict && ECMA_LETTER_ESCAPES.indexOf(n) < 0) {
                                throw new IllegalArgumentException("Invalid escape \\" + n + " in pattern: " + p);
                            }
                            sb.append('\\').append(n);
                        } else if (n >= '1' && n <= '9') {
                            sb.append('\\').append(n);
                        } else {
                            appendLiteral(n, sb);
                        }
                    }
                }
            } else if (inClass) {
                if (c == ']') {
                    inClass = false;
                    sb.append(c);
                } else if (c == '[' || c == '&') {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            } else if (c == '[') {
                if (p.startsWith("[^]", i)) {
                    sb.append("[\\s\\S]");
                    i += 2;
                } else if (p.startsWith("[]", i)) {
                    sb.append("(?!)");
                    i += 1;
                } else {
                    inClass = true;
                    sb.append(c);
                    if (i + 1 < len && p.charAt(i + 1) == '^') {
                        sb.append('^');
                        i++;
                    }
                    if (i + 1 < len && p.charAt(i + 1) == ']') {
                        sb.append("\\]");
                        i++;
                    }
                }
            } else if (strict && c == '(' && isGlobalFlagGroup(p, i)) {
                throw new IllegalArgumentException("Inline flag groups are not supported by ECMA-262: " + p);
            } else if (c == '$') {
                sb.append("\\z");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendLiteral(char c, StringBuilder sb) {
        if (Character.isLetterOrDigit(c)) {
            sb.append(c);
        } else {
            sb.append('\\').append(c);
        }
    }

    private static int appendProperty(String p, int i, char n, StringBuilder sb) {
        if (i + 1 >= p.length() || p.charAt(i + 1) != '{') {
            sb.append('\\').append(n);
            return i;
        }
        int close = p.indexOf('}', i);
        if (close < 0) {
            throw new IllegalArgumentException("Invalid property escape in pattern: " + p);
        }
        String name = p.substring(i + 2, close);
        String javaName;
        int eq = name.indexOf('=');
        if (eq > 0) {
            String key = name.substring(0, eq);
            String value = name.substring(eq + 1);
            javaName = switch (key) {
                case "General_Category", "gc" -> "gc=" + CATEGORY_NAMES.getOrDefault(value, value);
                case "Script", "sc", "Script_Extensions", "scx" -> "sc=" + value;
                default -> name;
            };
        } else {
            String category = CATEGORY_NAMES.get(name);
            if (category != null) {
                javaName = category;
            } else if (isJavaProperty(name)) {
                // also keeps Java specific classes such as \p{Alpha} working, as before
                javaName = name;
            } else {
                javaName = "Is" + name;
            }
        }
        sb.append('\\').append(n).append('{').append(javaName).append('}');
        return close;
    }

    private static boolean isGlobalFlagGroup(String p, int open) {
        if (open + 2 >= p.length() || p.charAt(open + 1) != '?') {
            return false;
        }
        int i = open + 2;
        while (i < p.length() && (isAsciiLetter(p.charAt(i)) || p.charAt(i) == '-')) {
            i++;
        }
        return i > open + 2 && i < p.length() && p.charAt(i) == ')';
    }

    private static boolean isJavaProperty(String name) {
        try {
            Pattern.compile("\\p{" + name + "}");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}

/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jsonschema.generator.aggregator;

import tools.jackson.core.JsonPointer;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.SourceVersion;
import io.micronaut.jsonschema.model.Schema;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.model.Schema.THIS_SCHEMA_REF;
import static io.micronaut.jsonschema.model.Schema.Type.NULL;
import static java.lang.String.join;

/**
 * An aggregator for deducing type information from json schema.
 *
 * @author Elif Kurtay
 * @since 1.3
 */
@Internal
public final class TypeAggregator {

    public static final Map<String, TypeDef> TYPE_MAP;

    public static final Map<String, TypeDef> TYPE_MAP_NULLABLE = CollectionUtils.mapOf(
        "integer", TypeDef.Primitive.INT_WRAPPER,
        "boolean", TypeDef.Primitive.BOOLEAN_WRAPPER,
        "array", TypeDef.of(List.class),
        "void", TypeDef.VOID,
        "string", TypeDef.STRING,
        "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT_WRAPPER,
        "null", TypeDef.OBJECT
    );
    private static final Pattern ALPHANUMERIC_RUN = Pattern.compile("[A-Za-z0-9]+");

    static {
        TYPE_MAP = new HashMap<>();
        TYPE_MAP.putAll(TYPE_MAP_NULLABLE);
        TYPE_MAP.put("integer", TypeDef.Primitive.INT);
        TYPE_MAP.put("boolean", TypeDef.Primitive.BOOLEAN);
        TYPE_MAP.put("number", TypeDef.Primitive.FLOAT);
    }

    /**
     * Extracts a Java type definition ({@code TypeDef}) from a given JSON schema.
     * This method analyzes the schema's type, format, and other properties to determine
     * the appropriate Java type representation, handling multiple special cases such as
     * type combinations, format-specific mappings, references, and "oneOf"/"anyOf" schemas.
     *
     * If the schema defines multiple types, the method defaults to `java.lang.Object`.
     * For specific formats like "date-time" or "uuid", the method maps to appropriate
     * Java classes such as `ZonedDateTime` or `UUID`. Additionally, schema references (`$ref`)
     * are resolved, potentially triggering the generation of new schema definitions.
     *
     * The method also handles special cases for numerical types with patterns and
     * handles the "oneOf" and "anyOf" schema keywords by recursively determining
     * the appropriate type from the valid options.
     *
     * @param schema the JSON schema to be converted into a Java type definition
     * @param context the generator context, which holds existing type definitions and supports
     *                the resolution of schema references
     * @return the corresponding {@code TypeDef} representing the schema as a Java type
     * @throws IllegalArgumentException if the schema's type is unsupported or invalid
     */
    public static TypeDef getTypeDefFromJson(Schema schema, GeneratorContext context) {
        // check oneOf, anyOf (allOf is already merged into during mapping)
        if (schema.hasOneOf()) {
            context.warn("UNSUPPORTED_KEYWORD", "oneOf is not supported at property level; using java.lang.Object");
            return TypeDef.OBJECT;
        } else if (schema.hasAnyOf()) {
            context.warn("UNSUPPORTED_KEYWORD", "anyOf is not supported at property level; using java.lang.Object");
            return TypeDef.OBJECT;
        } else if (schema.isEnum()) {
            return TypeDef.OBJECT;
        }

        // check for "type"
        boolean nullable = false;
        if (schema.hasType() && schema.getType().size() > 1) {
            if (schema.getType().size() == 2 && schema.getType().contains(NULL)) {
                nullable = true;
                schema.setNullable(true);
                var typeList = new java.util.ArrayList<>(schema.getType());
                typeList.remove(NULL);
                schema.setType(typeList);
            } else {
                context.warn("UNSUPPORTED_KEYWORD", "Multiple non-null JSON Schema types are not supported at property level; using java.lang.Object");
                return TypeDef.OBJECT;
            }
        }
        var type = schema.hasType() ? schema.getType().get(0) : Schema.Type.OBJECT;
        TypeDef typeDef;
        if (type.equals(Schema.Type.STRING) && schema.getFormat() != null) {
            var format = schema.getFormat();
            typeDef = switch (format) {
                case "date" -> ClassTypeDef.of(LocalDate.class);
                case "date-time", "time" -> ClassTypeDef.of(ZonedDateTime.class);
                case "duration" -> ClassTypeDef.of(Duration.class);
                case "ipv4" -> ClassTypeDef.of(java.net.Inet4Address.class);
                case "ipv6" -> ClassTypeDef.of(java.net.Inet6Address.class);
                case "uuid" -> ClassTypeDef.of(UUID.class);
                case "uri", "iri" -> ClassTypeDef.of(URI.class);
                case "json-pointer" -> ClassTypeDef.of(JsonPointer.class);
                // missing: web hostname, uri-reference, uri-template, regex
                default -> TypeDef.STRING;
            };
        } else if (type.equals(Schema.Type.NUMBER) && schema.getPattern() != null) {
            if (schema.getPattern().contains(".")) {
                typeDef = nullable ? TypeDef.Primitive.FLOAT_WRAPPER : TypeDef.Primitive.FLOAT;
            } else {
                typeDef = nullable ? TypeDef.Primitive.INT_WRAPPER : TypeDef.Primitive.INT;
            }
        } else if (schema.has$ref()) {
            String ref = schema.get$ref();
            if (ref.equals(THIS_SCHEMA_REF)) {
                return TypeDef.THIS;
            } else if (ref.indexOf("#") == 0) {
                ref = SourceGenerator.getInputFileName() + ref;
                if (!context.hasDefinition(ref)) {
                    context.warn("UNSUPPORTED_KEYWORD", "Local $ref is not resolved: " + schema.get$ref());
                    return TypeDef.OBJECT;
                }
            } else {
                context.warn("UNSUPPORTED_KEYWORD", "External $ref is not resolved: " + ref);
                return TypeDef.OBJECT;
            }
            typeDef = context.getDefinitionType(ref);
        } else if (schema.hasConstValue()) {
            Object constVal = schema.getConstValue();
            if (constVal instanceof Boolean) {
                typeDef = nullable ? TypeDef.Primitive.BOOLEAN_WRAPPER : TypeDef.Primitive.BOOLEAN;
            } else if (constVal instanceof Number num) {
                if (num instanceof Integer || num instanceof Long) {
                    typeDef = nullable ? TypeDef.Primitive.INT_WRAPPER : TypeDef.Primitive.INT;
                } else {
                    typeDef = nullable ? TypeDef.Primitive.FLOAT_WRAPPER : TypeDef.Primitive.FLOAT;
                }
            } else if (constVal instanceof String) {
                typeDef = TypeDef.STRING;
            } else {
                typeDef = TypeDef.OBJECT;
            }
        } else {
            String typeKey = type.toString().toLowerCase(Locale.ENGLISH);
            if (nullable) {
                typeDef = TYPE_MAP_NULLABLE.get(typeKey);
            } else {
                typeDef = TYPE_MAP.get(typeKey);
            }
        }
        // throw an error in case there is an unknown type
        if (typeDef == null) {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return typeDef;
    }

    public static String getConstantName(String input) {
        if (input.equals(input.toUpperCase())) {
            return input;
        }
        String cleanedInput = input.replaceAll("[-_]", " ")
            .replaceAll("(?<!^)(?=[A-Z])", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        while (!Character.isJavaIdentifierStart(cleanedInput.charAt(0))) {
            cleanedInput = cleanedInput.substring(1);
        }

        // Split into words
        String[] words = cleanedInput.split("\\s+");
        try {
            // Check if the input is acceptable
            if (words.length == 0 || words[0].isEmpty()) {
                throw new IllegalArgumentException();
            }
            for (int i = 0; i < words.length; i++) {
                words[i] = words[i].toUpperCase();
            }
            return join("_", words);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The enum constant name is not an acceptable identifier name.");
        }
    }

    public static String getClassName(String input) {
        var identifier = getPropertyName(input);
        while (!Character.isLetter(identifier.charAt(0))) {
            identifier = identifier.substring(1);
        }
        return capitalize(identifier);
    }

    public static String getPropertyName(String input) {
        if (input == null || input.isBlank()) {
            return "_";
        }
        boolean hasSeparator = !input.chars().allMatch(Character::isLetterOrDigit);
        if (!hasSeparator && SourceVersion.isName(input) && !isReservedJavaLiteral(input)) {
            return input;
        }
        Matcher matcher = ALPHANUMERIC_RUN.matcher(input);
        List<String> words = new java.util.ArrayList<>();
        while (matcher.find()) {
            words.add(matcher.group());
        }
        if (words.isEmpty()) {
            return "_";
        }
        StringBuilder camelCaseString = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i).trim();
            if (!word.isEmpty()) {
                if (i == 0) {
                    camelCaseString.append(word.toLowerCase());
                } else {
                    camelCaseString.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
                }
            }
        }
        if (camelCaseString.isEmpty()) {
            return "_";
        }
        if (!Character.isJavaIdentifierStart(camelCaseString.charAt(0))) {
            camelCaseString.insert(0, '_');
        }
        for (int i = 1; i < camelCaseString.length(); i++) {
            if (!Character.isJavaIdentifierPart(camelCaseString.charAt(i))) {
                camelCaseString.setCharAt(i, '_');
            }
        }
        String name = camelCaseString.toString();
        if (SourceVersion.isKeyword(name) || isReservedJavaLiteral(name)) {
            return name + "_";
        }
        return name;
    }

    private static boolean isReservedJavaLiteral(String value) {
        return "true".equals(value) || "false".equals(value) || "null".equals(value);
    }

    public static String unicodeToString(String input) {
        StringBuilder newName = new StringBuilder();
        for (Character c : input.toCharArray()) {
            if (c == '-' || c == '_') {
                newName.append('_');
            } else if (!Character.isLetter(c)) {
                String charName = Character.getName(c);
                newName.append(' ')
                    .append(charName, 0, charName.lastIndexOf(' ') != -1 ? charName.lastIndexOf(' ') : charName.length())
                    .append(' ');
            } else {
                newName.append(Character.toUpperCase(c));
            }
        }
        String cleanedInput = newName.toString().replaceAll("[-_]", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        return cleanedInput.replaceAll(" ", "_");
    }

    public static boolean isOnlyLetters(String input) {
        boolean isLetters = true;
        for (Character c : input.toCharArray()) {
            if (!Character.isLetter(c) && !Character.isWhitespace(c)) {
                isLetters = false;
            }
        }
        return isLetters;
    }
}

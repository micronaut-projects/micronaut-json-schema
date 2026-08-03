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
import org.jspecify.annotations.Nullable;

import javax.lang.model.SourceVersion;
import io.micronaut.jsonschema.model.Schema;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.loaders.UrlLoader.isValidUrl;
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

    private static final String UNSUPPORTED_KEYWORD = "UNSUPPORTED_KEYWORD";

    static {
        TYPE_MAP = new HashMap<>();
        TYPE_MAP.putAll(TYPE_MAP_NULLABLE);
        TYPE_MAP.put("integer", TypeDef.Primitive.INT);
        TYPE_MAP.put("boolean", TypeDef.Primitive.BOOLEAN);
        TYPE_MAP.put("number", TypeDef.Primitive.FLOAT);
    }

    private TypeAggregator() {
        /* This utility class should not be instantiated */
    }

    /**
     * Extracts a Java type definition ({@code TypeDef}) from a given JSON schema.
     * This method analyzes the schema's type, format, and other properties to determine
     * the appropriate Java type representation, handling multiple special cases such as
     * type combinations, format-specific mappings, references, and "oneOf"/"anyOf" schemas.
     * <p>
     * If the schema defines multiple types, the method defaults to `java.lang.Object`.
     * For specific formats like "date-time" or "uuid", the method maps to appropriate
     * Java classes such as `ZonedDateTime` or `UUID`. Additionally, schema references (`$ref`)
     * are resolved, potentially triggering the generation of new schema definitions.
     * <p>
     * The method also handles special cases for numerical types with patterns and
     * handles the "oneOf" and "anyOf" schema keywords by recursively determining
     * the appropriate type from the valid options.
     *
     * @param schema  the JSON schema to be converted into a Java type definition
     * @param context the generator context, which holds existing type definitions and supports
     *                the resolution of schema references
     * @return the corresponding {@code TypeDef} representing the schema as a Java type
     * @throws IllegalArgumentException if the schema's type is unsupported or invalid
     */
    public static TypeDef getTypeDefFromJson(Schema schema, GeneratorContext context) {
        // check oneOf, anyOf (allOf is already merged into during mapping)
        if (schema.hasOneOf()) {
            if (!context.isJsonSchemaRecordsProfile()) {
                // Preserve the existing generator behavior: property-level oneOf is broad Object.
                return TypeDef.OBJECT;
            }
            // The record-generation profile accepts only the common nullable composition form,
            // oneOf: [{ "type": "null" }, { ...single non-null schema... }].
            if (!normalizeNullableOneOf(schema)) {
                context.warn(UNSUPPORTED_KEYWORD, "oneOf is not supported at property level; using java.lang.Object");
                return TypeDef.OBJECT;
            }
        }
        if (schema.hasAnyOf()) {
            if (!context.isJsonSchemaRecordsProfile()) {
                // Preserve the existing generator behavior for anyOf outside the record profile.
                TypeDef typeDef = chooseFromAnyOf(schema.getAnyOf(), context);
                return typeDef != null ? typeDef : TypeDef.OBJECT;
            }
            // The record-generation profile first handles nullable anyOf consistently with
            // type: ["T", "null"]; other anyOf shapes go through the existing broad chooser.
            if (!normalizeNullableAnyOf(schema)) {
                TypeDef typeDef = chooseFromAnyOf(schema.getAnyOf(), context);
                return typeDef != null ? typeDef : TypeDef.OBJECT;
            }
        } else if (schema.isEnum()) {
            return TypeDef.OBJECT;
        }

        // check for "type"
        boolean nullable = false;
        List<Schema.Type> schemaTypes = schema.getType();
        if (schemaTypes != null && schemaTypes.size() > 1) {
            if (schemaTypes.size() == 2 && schemaTypes.contains(NULL)) {
                nullable = true;
                if (context.isJsonSchemaRecordsProfile()) {
                    // Record generation treats type ["T", "null"] as value nullability,
                    // not as a Java union type.
                    schema.setNullable(true);
                }
                // Preserve the old generator's in-place list mutation. In the record profile,
                // copy first so we do not mutate a list while it may be shared by the parser/model.
                var typeList = context.isJsonSchemaRecordsProfile() ? new java.util.ArrayList<>(schema.getType()) : schema.getType();
                typeList.remove(NULL);
                schema.setType(typeList);
            } else {
                if (context.isJsonSchemaRecordsProfile()) {
                    context.warn(UNSUPPORTED_KEYWORD, "Multiple non-null JSON Schema types are not supported at property level; using java.lang.Object");
                } else {
                    System.err.println("Only one type is allowed per schema. " +
                        "In case of multiple types, the variable is generated as a java.lang.Object.");
                }
                return TypeDef.OBJECT;
            }
        }
        schemaTypes = schema.getType();
        var type = schemaTypes != null && !schemaTypes.isEmpty() ? schemaTypes.get(0) : Schema.Type.OBJECT;
        TypeDef typeDef;
        TypeDef oracleExtendedTypeDef = null;
        if (context.isJsonSchemaRecordsProfile() && schema.getFormat() == null) {
            oracleExtendedTypeDef = getOracleExtendedTypeDef(schema, type, nullable);
        }
        String format = schema.getFormat();
        if (type.equals(Schema.Type.STRING) && format != null) {
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
        } else if (oracleExtendedTypeDef != null) {
            typeDef = oracleExtendedTypeDef;
        } else if (type.equals(Schema.Type.NUMBER) && schema.getPattern() != null) {
            String pattern = Objects.requireNonNull(schema.getPattern());
            if (pattern.contains(".")) {
                typeDef = nullable ? TypeDef.Primitive.FLOAT_WRAPPER : TypeDef.Primitive.FLOAT;
            } else {
                typeDef = nullable ? TypeDef.Primitive.INT_WRAPPER : TypeDef.Primitive.INT;
            }
        } else if (schema.has$ref()) {
            String ref = Objects.requireNonNull(schema.get$ref());
            boolean localRef = ref.indexOf("#") == 0;
            if (ref.equals(THIS_SCHEMA_REF)) {
                return TypeDef.THIS;
            } else if (localRef) {
                ref = SourceGenerator.getInputFileName() + ref;
            }
            if (context.isJsonSchemaRecordsProfile()) {
                // The record pipeline prepares supported same-document refs before generation.
                // Anything still missing here cannot be represented precisely as a property type.
                if (!context.hasDefinition(ref)) {
                    context.warn(UNSUPPORTED_KEYWORD, (localRef ? "Local" : "External") + " $ref is not resolved: " + schema.get$ref());
                    return TypeDef.OBJECT;
                }
            } else {
                int fragmentIndex = ref.indexOf("#");
                if (fragmentIndex < 0 && !context.hasDefinition(ref)) {
                    return TypeDef.OBJECT;
                }
                var originalFileName = SourceGenerator.getInputFileName();
                if (fragmentIndex > 0 && !context.hasDefinition(ref)) {
                    var location = ref.substring(0, fragmentIndex);
                    if (!isValidUrl(location) || location.equals(originalFileName)) {
                        // Keep the previous fallback for unresolved local/current-file fragments.
                        typeDef = context.getDefinitionType(ref);
                        return typeDef;
                    }
                    try {
                        // Preserve legacy behavior: non-record generation may fetch referenced URL schemas.
                        var generator = new SourceGenerator(SourceGenerator.getLanguage(), context);
                        SourceGenerator.setInputFileName(location);
                        generator.generate(
                            context.getConfiguration().toBuilder().withInputStream(null)
                                .withInputFolder(null).withJsonUrl(location).withJsonFile(null).build());
                        SourceGenerator.setInputFileName(originalFileName);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
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

    private static TypeDef getOracleExtendedTypeDef(Schema schema, Schema.Type type, boolean nullable) {
        if (!schema.hasExtendedType()) {
            return null;
        }
        String extendedType = firstExtendedType(schema);
        if (extendedType == null || "null".equalsIgnoreCase(extendedType)) {
            return null;
        }
        return switch (extendedType.toLowerCase(Locale.ENGLISH)) {
            case "date", "timestamp" ->
                isStringLike(type) ? ClassTypeDef.of(LocalDateTime.class) : null;
            case "timestamptz" -> isStringLike(type) ? ClassTypeDef.of(ZonedDateTime.class) : null;
            case "dsinterval" -> isStringLike(type) ? ClassTypeDef.of(Duration.class) : null;
            case "yminterval" -> isStringLike(type) ? ClassTypeDef.of(Period.class) : null;
            case "binary" -> isStringLike(type) ? TypeDef.array(TypeDef.Primitive.BYTE) : null;
            case "double" ->
                isNumberLike(type) ? nullable ? ClassTypeDef.of(Double.class) : TypeDef.Primitive.DOUBLE : null;
            case "float" ->
                isNumberLike(type) ? nullable ? TypeDef.Primitive.FLOAT_WRAPPER : TypeDef.Primitive.FLOAT : null;
            default -> null;
        };
    }

    private static String firstExtendedType(Schema schema) {
        Object extendedType = schema.getExtendedType();
        if (extendedType instanceof String value) {
            return value;
        }
        if (extendedType instanceof List<?> values) {
            List<String> nonNullValues = values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(val -> !"null".equalsIgnoreCase(val))
                .distinct()
                .toList();
            if (nonNullValues.size() == 1) {
                return nonNullValues.get(0);
            }
        }
        return null;
    }

    private static boolean isStringLike(Schema.Type type) {
        return Schema.Type.STRING.equals(type) || Schema.Type.OBJECT.equals(type);
    }

    private static boolean isNumberLike(Schema.Type type) {
        return Schema.Type.NUMBER.equals(type) || Schema.Type.INTEGER.equals(type) || Schema.Type.OBJECT.equals(type);
    }

    private static boolean normalizeNullableOneOf(Schema schema) {
        if (schema.getOneOf().size() != 2) {
            return false;
        }
        Schema nonNullSchema = nullableCompositionBranch(schema.getOneOf());
        if (nonNullSchema == null) {
            return false;
        }
        // Replace the nullable composition wrapper with its non-null branch so the normal
        // type/annotation pipeline can produce @Nullable T instead of Object.
        schema.merge(nonNullSchema);
        schema.setOneOf(null);
        schema.setNullable(true);
        return true;
    }

    private static boolean normalizeNullableAnyOf(Schema schema) {
        if (schema.getAnyOf().size() != 2) {
            return false;
        }
        Schema nonNullSchema = nullableCompositionBranch(schema.getAnyOf());
        if (nonNullSchema == null) {
            return false;
        }
        // Replace the nullable composition wrapper with its non-null branch so the normal
        // type/annotation pipeline can produce @Nullable T instead of Object.
        schema.merge(nonNullSchema);
        schema.setAnyOf(null);
        schema.setNullable(true);
        return true;
    }

    /**
     * The strategy to choose from an anyOf keyword in Json Schema.
     * Needs improvement.
     * Current strategy:
     * if empty, return null,
     * if single schema, return that schema,
     * if 2 schema but one has type "NULL", return the non-null schema
     * if all schemas has the same type, return that common base type
     * else return empty Object schema.
     *
     * @param schemas List of Schemas in the anyOf keyword
     * @return chosen Schema
     */
    @Nullable
    private static TypeDef chooseFromAnyOf(List<Schema> schemas, GeneratorContext context) {
        if (schemas.isEmpty()) {
            return null;
        } else if (schemas.size() == 1) {
            return getTypeDefFromJson(schemas.get(0), context);
        } else if (context.isJsonSchemaRecordsProfile() && schemas.size() == 2) {
            // This path catches nullable anyOf when chooseFromAnyOf is reached directly,
            // for example from legacy-compatible branches above.
            Schema nonNullSchema = nullableCompositionBranch(schemas);
            if (nonNullSchema != null) {
                nonNullSchema.setNullable(true);
                return getTypeDefFromJson(nonNullSchema, context);
            }
        } else if (schemas.size() == 2) {
            Schema first = schemas.get(0);
            Schema second = schemas.get(1);
            if (isNullSchema(first)) {
                return getTypeDefFromJson(second, context);
            } else if (isNullSchema(second)) {
                return getTypeDefFromJson(first, context);
            }
        }
        boolean sameType = true;
        for (var i = 0; i < schemas.size() - 1; i++) {
            List<Schema.Type> currentTypes = schemas.get(i).getType();
            List<Schema.Type> nextTypes = schemas.get(i + 1).getType();
            if (currentTypes != null && !currentTypes.equals(nextTypes)) {
                sameType = false;
            } else if (currentTypes == null || nextTypes == null) {
                sameType = false;
            }
        }
        if (sameType) {
            List<Schema.Type> schemaTypes = Objects.requireNonNull(schemas.get(0).getType());
            var type = schemaTypes.get(0);
            // Same scalar alternatives can share a broad base type. Object alternatives still
            // lose shape information, so record generation records a warning.
            TypeDef typeDef = TYPE_MAP.get(type.toString().toLowerCase(Locale.ENGLISH));
            if (context.isJsonSchemaRecordsProfile() && TypeDef.OBJECT.equals(typeDef)) {
                context.warn(UNSUPPORTED_KEYWORD, "anyOf object alternatives are not supported at property level; using java.lang.Object");
            }
            return typeDef;
        }
        if (context.isJsonSchemaRecordsProfile()) {
            context.warn(UNSUPPORTED_KEYWORD, "anyOf alternatives cannot be modeled deterministically at property level; using java.lang.Object");
        }
        return TypeDef.OBJECT;
    }

    private static Schema nullableCompositionBranch(List<Schema> schemas) {
        Schema nonNullSchema = null;
        boolean nullSchemaFound = false;
        for (Schema candidate : schemas) {
            if (isNullSchema(candidate)) {
                nullSchemaFound = true;
            } else if (nonNullSchema == null) {
                nonNullSchema = candidate;
            } else {
                return null;
            }
        }
        return nullSchemaFound ? nonNullSchema : null;
    }

    private static boolean isNullSchema(Schema schema) {
        List<Schema.Type> types = schema.getType();
        return types != null && types.size() == 1 && types.contains(NULL);
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
        if (SourceVersion.isName(input)) {
            return input;
        }
        if (SourceVersion.isKeyword(input)) {
            return input + "_json";
        }
        String cleanedInput = input.replaceAll("[-_]", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        while (!Character.isJavaIdentifierStart(cleanedInput.charAt(0))) {
            cleanedInput = cleanedInput.substring(1);
        }

        // Split into words
        String[] words = cleanedInput.split("\\s+");
        StringBuilder camelCaseString = new StringBuilder();

        // Check if the input is acceptable
        if (words.length == 0 || words[0].isEmpty()) {
            throw new IllegalArgumentException("Property name is not an acceptable variable name");
        }

        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            if (!word.isEmpty()) {
                if (i == 0) {
                    camelCaseString.append(word.toLowerCase());
                } else {
                    camelCaseString.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
                }
            }
        }
        return camelCaseString.toString();
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

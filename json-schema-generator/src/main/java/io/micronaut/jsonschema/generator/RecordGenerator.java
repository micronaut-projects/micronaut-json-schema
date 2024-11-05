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
package io.micronaut.jsonschema.generator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationInfoAggregator;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;
import jakarta.inject.Singleton;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.micronaut.core.util.StringUtils.capitalize;
import static java.lang.String.join;

/**
 * A generator to create Java Beans from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */

@Internal
@Singleton
public final class RecordGenerator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN, "array", TypeDef.of(List.class),
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});

    // TODO objectName and fileName should match. Perhaps we should just take output directory as argument. The argument does not need to be optional then
    // TODO take language as argument.
    public boolean generate(InputStream inputStream, Optional<File> outputFileLocation) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        return generateFromSchemaMap(jsonSchema, outputFileLocation);
    }

    public boolean generate(File jsonFileLocation, Optional<File> outputFileLocation) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        return generateFromSchemaMap(jsonSchema, outputFileLocation);
    }

    private Map<String, ?> getJsonSchema(InputStream inputStream, File schemaFile) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();
        if (inputStream != null) {
            String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return (Map<String, ?>) jsonMapper.readValue(jsonString, HashMap.class);
        } else if (schemaFile != null) {
            return (Map<String, ?>) jsonMapper.readValue(schemaFile, HashMap.class);
        }
        return null;
    }

    public boolean generateFromSchemaMap(Map<String, ?> jsonSchema, Optional<File> outputFileLocation) throws IOException {
        try {
            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator == null) {
                return false;
            }

            // TODO configure package as argument
            String packageName = "test";
            String objectName = capitalize(getCamelCaseName(jsonSchema.get("title").toString()));

            File outputFile = getOutputFile(outputFileLocation,
                (packageName + ".").replace('.', File.separatorChar) + objectName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                if (jsonSchema.containsKey("enum")) {
                    var objectDef = buildEnum(jsonSchema, packageName + "." + objectName);
                    sourceGenerator.write(objectDef, writer);
                } else {
                    var objectDef = buildRecord(jsonSchema, packageName + "." + objectName);
                    sourceGenerator.write(objectDef, writer);
                }
            }
            return true;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    private static File getOutputFile(Optional<File> outputFileLocation, String objectName) throws IOException {
        File outputFile = outputFileLocation.orElse(null);
        if (outputFile == null) { // default file
            outputFile = new File(objectName + ".java");
            outputFile.getParentFile().mkdirs();
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    private EnumDef buildEnum(Map<String, ?> jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(capitalize(builderClassName))
            .addModifiers(Modifier.PUBLIC);
        boolean isComplexEnum = false;
        Map<ExpressionDef.Constant, ExpressionDef> cases = new HashMap<>();
        for (Object anEnum : ((List<?>) jsonSchema.get("enum"))) {
            String constName = getConstantName(anEnum.toString());
            if (constName.equals(anEnum.toString())) {
                enumBuilder.addEnumConstant(constName);
            } else {
                enumBuilder.addEnumConstant(constName, ExpressionDef.constant(anEnum.toString()));
                cases.put(ExpressionDef.constant(anEnum.toString()), new VariableDef.Constant(TypeDef.THIS, constName));
                isComplexEnum = true;
            }
        }
        if (isComplexEnum) {
            enumBuilder.addField(FieldDef.builder("name")
                    .ofType(TypeDef.STRING)
                    .addModifiers(Modifier.PUBLIC)
                    .build())
                .addAllFieldsConstructor(Modifier.PRIVATE)
                .addMethod(MethodDef.builder("getName")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(JsonValue.class)
                    .returns(TypeDef.STRING)
                    .build((aThis, parameters) ->
                        aThis.field("name", TypeDef.STRING).returning()))
                .addMethod(MethodDef.builder("statusOf")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(JsonCreator.class)
                    .returns(TypeDef.THIS)
                    .addParameter("name", TypeDef.STRING)
                    .build((aThis, parameters) ->
                        parameters.get(0).asExpressionSwitch(TypeDef.STRING, cases).returning()
                    ));
        }
        if (jsonSchema.containsKey("properties")) {
            Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
            List<String> requiredProperties;
            if (jsonSchema.containsKey("required")) {
                requiredProperties = (List<String>) jsonSchema.get("required");
            } else {
                requiredProperties = new ArrayList<>();
            }
            properties.entrySet().forEach(entry ->
                addField(enumBuilder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));
        }
        return enumBuilder.build();
    }

    private RecordDef buildRecord(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        if (jsonSchema.containsKey("properties")) {
            Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
            List<String> requiredProperties;
            if (jsonSchema.containsKey("required")) {
                requiredProperties = (List<String>) jsonSchema.get("required");
            } else {
                requiredProperties = new ArrayList<>();
            }
            properties.entrySet().forEach(entry ->
                addField(objectBuilder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));
        }
        return objectBuilder.build();
    }

    private void addField(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description, boolean isRequired) {
        String name = getCamelCaseName(propertyName);

        TypeDef propertyType = getTypeDefFromJson(description);
        if (description.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, name, description);
        }
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(name);
        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getTypeDef(objectBuilder, propertyName, description);
            propertyDef.ofType(propertyType);
            AnnotationInfoAggregator.addAnnotations(propertyDef, description, TypeDef.of(List.class), isRequired);
        } else {
            propertyDef.ofType(propertyType);
            AnnotationInfoAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        }

        if (!name.equals(propertyName)) {
            AnnotationDef annotationDef = AnnotationDef.builder(JsonProperty.class).addMember("value", propertyName).build();
            propertyDef.addAnnotation(annotationDef);
        }
        objectBuilder.addProperty(propertyDef.build());
    }

    private TypeDef getTypeDef(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        var items = (Map<String, Object>) description.get("items");
        Class listClass = List.class;
        if (description.containsKey("uniqueItems") && description.get("uniqueItems").toString().equals("true")) {
            listClass = Set.class;
        }

        TypeDef propertyType = getTypeDefFromJson(items);
        if (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getTypeDef(objectBuilder, propertyName, items);
        } else if (items.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, propertyName, items);
        } else if (propertyType instanceof TypeDef.Primitive primitive) {
            propertyType = primitive.wrapperType();
        }

        var annotations = AnnotationInfoAggregator.getAnnotations(items, propertyType);
        return TypeDef.parameterized(listClass, propertyType.annotated(annotations));
    }

    private TypeDef getEnumType(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        EnumDef enumDef = buildEnum(description, propertyName);
        objectBuilder.addInnerType(enumDef);
        return enumDef.asTypeDef();
    }

    private static TypeDef getTypeDefFromJson(Map<String, Object> description) {
        var type = description.getOrDefault("type", "object");
        String typeName;
        if (type.getClass() == ArrayList.class) {
            typeName = ((ArrayList<?>) type).get(0).toString();
        } else {
            typeName = type.toString();
        }
        if (typeName.equals("string") && description.containsKey("format")) {
            var format = description.get("format").toString();
            switch (format) {
                case "date": return ClassTypeDef.of(LocalDate.class);
                case "date-time", "time": return ClassTypeDef.of(ZonedDateTime.class);
                case "duration": return ClassTypeDef.of(Duration.class);
                case "ipv4": return ClassTypeDef.of(java.net.Inet4Address.class);
                case "ipv6": return ClassTypeDef.of(java.net.Inet6Address.class);
                case "uuid": return ClassTypeDef.of(UUID.class);
                case "uri", "iri": return ClassTypeDef.of(URI.class);
                case "json-pointer": return ClassTypeDef.of(JsonPointer.class);
                // missing: email, web hostname, uri-reference, uri-template, regex
            }
        }
        return TYPE_MAP.get(typeName);
    }

    private static String getConstantName(String input) {
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
                throw new IllegalArgumentException("The enum constant name is not an acceptable identifier name.");
            }
            for (int i = 0; i < words.length; i++) {
                words[i] = words[i].toUpperCase();
            }
            return join("_", words);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    private static String getCamelCaseName(String input) {
        if (SourceVersion.isName(input)) {
            return input;
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
}

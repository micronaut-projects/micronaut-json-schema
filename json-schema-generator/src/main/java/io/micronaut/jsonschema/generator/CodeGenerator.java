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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;
import jakarta.inject.Singleton;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.addDefinition;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.clearAllDefinitions;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.getDefinitionType;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.hasDefinition;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getCamelCaseName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getConstantName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getEnumType;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDef;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getFileName;

/**
 * A generator to create Java Beans from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
@Singleton
public final class CodeGenerator {

    private enum ObjectType { CLASS, RECORD, INTERFACE, ENUM }
    private SourceGenerator sourceGenerator;
    private String discriminatorProperty = "";

    private void initializeGenerator(VisitorContext.Language language) {
        sourceGenerator = SourceGenerators.findByLanguage(language).orElse(null);
        if (sourceGenerator == null) {
            throw new RuntimeException("No source generator found for language " + language);
        }
    }

    /**
     * A method for creating a single record from a json schema. Used mainly in testing.
     *
     * @param inputStream The input stream of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param fileName The fileName for the output file
     * @param language The desired language for record to be generated in
     * @return The generated file
     */
    public File generate(InputStream inputStream, Path outputPath, String packageName, String fileName, VisitorContext.Language language) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        initializeGenerator(language);

        File outputFile = getOutputFile(outputPath, packageName, fileName);
        if (jsonSchema.containsKey("enum")) {
            return generateFromSchemaMap(jsonSchema, outputFile, ObjectType.ENUM);
        }
        return generateFromSchemaMap(jsonSchema, outputFile, ObjectType.RECORD);
    }

    /**
     * A method for creating a single record from a json schema.
     *
     * @param jsonFileLocation The input file location of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param fileName The fileName for the output file
     * @param language The desired language for record to be generated in
     * @return The number of generated files
     */
    public File generate(File jsonFileLocation, Path outputPath, String packageName, String fileName, VisitorContext.Language language) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        initializeGenerator(language);

        File outputFile = getOutputFile(outputPath, packageName, fileName);
        if (jsonSchema.containsKey("enum")) {
            return generateFromSchemaMap(jsonSchema, outputFile, ObjectType.ENUM);
        }
        return generateFromSchemaMap(jsonSchema, outputFile, ObjectType.RECORD);
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param inputStream The input stream of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param language The desired language for record to be generated in
     * @return The number of generated files
     */
    public int generate(InputStream inputStream, Path outputPath, String packageName, VisitorContext.Language language) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        initializeGenerator(language);
        return generateFolder(jsonSchema, outputPath, packageName, language);
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param jsonFileLocation The input file location of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param language The desired language for record to be generated in
     * @return The number of generated files
     */
    public int generate(File jsonFileLocation, Path outputPath, String packageName, VisitorContext.Language language) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        initializeGenerator(language);
        return generateFolder(jsonSchema, outputPath, packageName, language);
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

    private int generateFolder(Map<String, ?> jsonSchema, Path outputPath, String packageName, VisitorContext.Language language) throws IOException {
        AtomicInteger generatedClassCount = new AtomicInteger();
        HashSet<String> oneOfSet = new HashSet<>();
        if (jsonSchema.containsKey("oneOf")) {
            // TODO: add no-reference types
            var oneOfRefs = (List<Map<String, String>>) jsonSchema.get("oneOf");
            oneOfRefs.forEach(oneOf -> oneOfSet.add(oneOf.get("$ref")));
        }
        // save all definition types
        if (jsonSchema.containsKey("definitions")) {
            // TODO: add no-reference types
            var definitions = (Map<String, Map<String, Object>>) jsonSchema.get("definitions");
            definitions.forEach((key, value) -> {
                if (key.equals("ResourceList")) {
                    return;
                }
                TypeDef typeOfDefinition = getTypeDefFromJson(value);
                if (!typeOfDefinition.isPrimitive() && !typeOfDefinition.equals(TypeDef.STRING)) {
                    addDefinition("#/definitions/" + key, ClassTypeDef.of(capitalize(key)));
                } else {
                    addDefinition("#/definitions/" + key, value);
                }
            });
        }

        // generate top level schema
        if (jsonSchema.containsKey("enum")) {
            generatedClassCount.getAndIncrement();
            String fileName = getFileName(jsonSchema, language);
            File outputFile = getOutputFile(outputPath, packageName, fileName);
            generateFromSchemaMap(jsonSchema, outputFile, ObjectType.ENUM);
        } else if (jsonSchema.containsKey("type") || jsonSchema.containsKey("properties")) {
            generatedClassCount.getAndIncrement();
            String fileName = getFileName(jsonSchema, language);
            File outputFile = getOutputFile(outputPath, packageName, fileName);
            generateFromSchemaMap(jsonSchema, outputFile, ObjectType.CLASS);

            // save superclass definition for inheritance
            String className = fileName.substring(0, outputFile.getName().lastIndexOf('.'));
            addDefinition("superClass", ClassTypeDef.of(className));
        } else if (jsonSchema.containsKey("oneOf")) {
            generatedClassCount.getAndIncrement();
            String fileName = getFileName(jsonSchema, language);
            File outputFile = getOutputFile(outputPath, packageName, fileName);
            generateFromSchemaMap(jsonSchema, outputFile, ObjectType.INTERFACE);

            // save superinterface definition for inheritance
            String className = fileName.substring(0, outputFile.getName().lastIndexOf('.'));
            addDefinition("superInterface", ClassTypeDef.of(className));
        }

        // generate classes in definitions
        if (jsonSchema.containsKey("definitions")) {
            var definitions = (Map<String, Map<String, Object>>) jsonSchema.get("definitions");
            definitions.entrySet()
                .stream()
                .filter(definition -> {
                    if (definition.getKey().equals("ResourceList")) {
                        return false;
                    }
                    TypeDef typeOfDefinition = getDefinitionType("#/definitions/" + definition.getKey());
                    assert typeOfDefinition != null;
                    boolean isClass = !typeOfDefinition.isPrimitive() && !typeOfDefinition.equals(TypeDef.STRING);

                    // update definition with annotations
                    var annotations = AnnotationsAggregator.getAnnotations(definition.getValue(), typeOfDefinition);
                    if (!annotations.isEmpty()) {
                        addDefinition("#/definitions/" + definition.getKey(), typeOfDefinition.annotated(annotations));
                    }
                    return isClass;
                }).forEach(definition -> {
                    try {
                        generatedClassCount.getAndIncrement();
                        File outputFile = getOutputFile(outputPath, packageName, capitalize(definition.getKey()) + ".java");
                        ObjectType generationType = (oneOfSet.contains("#/definitions/" + definition.getKey())) ? ObjectType.CLASS : ObjectType.RECORD;
                        generateFromSchemaMap(definition.getValue(), outputFile, generationType);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        clearAllDefinitions();
        return generatedClassCount.get();
    }

    private static File getOutputFile(Path outputPath, String packageName, String fileName) throws IOException {
        // Create full path
        String packagePath = packageName.replace('.', File.separatorChar);
        Path fullPath = outputPath.resolve(packagePath).resolve(fileName);

        // Create directories if they do not exist
        File outputFile = fullPath.toFile();
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    private File generateFromSchemaMap(Map<String, ?> jsonSchema, File outputFile, ObjectType objectType) throws IOException {
        try {
            String className = outputFile.getName().substring(0, outputFile.getName().lastIndexOf('.'));

            try (FileWriter writer = new FileWriter(outputFile)) {
                ObjectDef objectDef = switch (objectType) {
                    case ENUM -> buildEnum(jsonSchema, className);
                    case CLASS -> buildClass(jsonSchema, className);
                    case INTERFACE -> buildInterface(jsonSchema, className);
                    default -> buildRecord(jsonSchema, className);
                };
                sourceGenerator.write(objectDef, writer);
            }
            return outputFile;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public static EnumDef buildEnum(Map<String, ?> jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(capitalize(builderClassName))
            .addModifiers(Modifier.PUBLIC);
        boolean isComplexEnum = false;
        LinkedHashMap<ExpressionDef.Constant, ExpressionDef> cases = new LinkedHashMap<>();
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
        addFields(jsonSchema, enumBuilder);
        return enumBuilder.build();
    }

    private RecordDef buildRecord(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private ClassDef buildClass(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        ClassDef.ClassDefBuilder objectBuilder = ClassDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        if (hasDefinition("superClass")) {
            var superClass = getDefinitionType("superClass");
            objectBuilder.superclass((ClassTypeDef) superClass);
        } else if (hasDefinition("superInterface")) {
            var superInterface = getDefinitionType("superInterface");
            objectBuilder.addSuperinterface(superInterface);
        } else {
            // top level class
            addDiscriminatorAnnotations(jsonSchema, objectBuilder);
        }

        if (!discriminatorProperty.isBlank()) {
            AnnotationDef jsonTypeInfo = AnnotationDef.builder(JsonTypeInfo.class)
                .addMember("use", JsonTypeInfo.Id.NAME)
                .addMember("property", discriminatorProperty)
                .build();
            objectBuilder.addAnnotation(jsonTypeInfo);
        }

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private InterfaceDef buildInterface(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        InterfaceDef.InterfaceDefBuilder objectBuilder = InterfaceDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);
        if (jsonSchema.containsKey("discriminator")) {
            // top level interface
            addDiscriminatorAnnotations(jsonSchema, objectBuilder);
            if (!discriminatorProperty.isBlank()) {
                AnnotationDef jsonTypeInfo = AnnotationDef.builder(JsonTypeInfo.class)
                    .addMember("use", JsonTypeInfo.Id.NAME)
                    .addMember("property", discriminatorProperty)
                    .build();
                objectBuilder.addAnnotation(jsonTypeInfo);
            }
        }
        return objectBuilder.build();
    }

    private static void addFields(Map<String, ?> jsonSchema, ObjectDefBuilder builder) {
        if (jsonSchema.containsKey("properties")) {
            Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
            List<String> requiredProperties;
            if (jsonSchema.containsKey("required")) {
                requiredProperties = (List<String>) jsonSchema.get("required");
            } else {
                requiredProperties = new ArrayList<>();
            }
            properties.entrySet().forEach(entry ->
                addField(builder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));

            if (jsonSchema.containsKey("additionalProperties") && !Objects.equals(jsonSchema.get("additionalProperties").toString(), "false")) {
                if (Objects.equals(jsonSchema.get("additionalProperties").toString(), "true")) {
                    builder.addProperty(
                        PropertyDef.builder("additionalProperties")
                            .ofType(TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING, TypeDef.OBJECT))
                            .build()
                    );
                } else {
                    Map<String, Object> map = (Map<String, Object>) jsonSchema.get("additionalProperties");
                    TypeDef type = getTypeDefFromJson(map);
                    builder.addProperty(
                        PropertyDef.builder("additionalProperties")
                            .ofType(TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING, type))
                            .build()
                    );
                }
            }
        }
    }

    private static void addField(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description, boolean isRequired) {
        if (propertyName.equals("resourceType")) {
            return;
        }
        String name = getCamelCaseName(propertyName);

        TypeDef propertyType = getTypeDefFromJson(description);
        if (description.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, name, description);
        }
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(name);
        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getTypeDef(objectBuilder, propertyName, description);
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, TypeDef.of(List.class), isRequired);
        } else {
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        }

        if (!name.equals(propertyName)) {
            AnnotationDef annotationDef = AnnotationDef.builder(JsonProperty.class).addMember("value", propertyName).build();
            propertyDef.addAnnotation(annotationDef);
        }
        objectBuilder.addProperty(propertyDef.build());
    }

    private void addDiscriminatorAnnotations(Map<String, ?> jsonSchema, ObjectDefBuilder objectBuilder) {
        if (!jsonSchema.containsKey("discriminator")) {
            return;
        }
        var discriminator = (Map<String, ?>) jsonSchema.get("discriminator");
        discriminatorProperty = (String) discriminator.get("propertyName");

        var mapping = (Map<String, String>) discriminator.get("mapping");
        List<AnnotationDef> subTypeList = mapping.entrySet()
            .stream()
            .map(entry -> AnnotationDef
                .builder(JsonSubTypes.Type.class)
                .addMember("value", getDefinitionType(entry.getValue()))
                .addMember("name", entry.getKey())
                .build())
            .toList();
        AnnotationDef jsonSubTypes = AnnotationDef.builder(JsonSubTypes.class).addMember("value", subTypeList).build();
        objectBuilder.addAnnotation(jsonSubTypes);
    }
}

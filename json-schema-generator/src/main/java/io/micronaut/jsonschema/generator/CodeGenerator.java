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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
import io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.*;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.*;

/**
 * A generator to create Java Beans from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
public final class CodeGenerator {

    private enum ObjectType { CLASS, RECORD, INTERFACE, ENUM }
    private final SourceGenerator sourceGenerator;
    private final VisitorContext.Language language;
    private String discriminatorProperty = "";

    public CodeGenerator(VisitorContext.Language language) {
        sourceGenerator = SourceGenerators.findByLanguage(language).orElse(null);
        if (sourceGenerator == null) {
            throw new RuntimeException("No source generator found for language " + language);
        }
        this.language = language;
    }

    /**
     * A method for creating a single record from a json schema. Used mainly in testing.
     *
     * @param inputStream The input stream of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param fileName The fileName for the output file
     * @return The generated file
     */
    public File generate(InputStream inputStream, Path outputPath, String packageName, String fileName) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return generateFromSchemaMap(jsonSchema, outputPath, packageName, new String[]{fileName});
    }

    /**
     * A method for creating a single record from a json schema.
     *
     * @param jsonFileLocation The input file location of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @param fileName The fileName for the output file
     * @return The number of generated files
     */
    public File generate(File jsonFileLocation, Path outputPath, String packageName, String fileName) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return generateFromSchemaMap(jsonSchema, outputPath, packageName, new String[]{fileName});
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param inputStream The input stream of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @return The number of generated files
     */
    public int generate(InputStream inputStream, Path outputPath, String packageName) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        return generateFolder(jsonSchema, outputPath, packageName);
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param jsonFileLocation The input file location of a json schema
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @return The number of generated files
     */
    public int generate(File jsonFileLocation, Path outputPath, String packageName) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        return generateFolder(jsonSchema, outputPath, packageName);
    }

    private int generateFolder(Map<String, ?> jsonSchema, Path outputPath, String packageName) throws IOException {
        AtomicInteger generatedClassCount = new AtomicInteger();
        final String[] topLevelName = {""};

        // save all definition and oneOf types
        if (jsonSchema.containsKey("oneOf")) {
            var oneOfRefs = (List<Map<String, Object>>) jsonSchema.get("oneOf");
            oneOfRefs.forEach(oneOf -> {
                if (oneOf.containsKey("$ref")) {
                    addOneOf((String) oneOf.get("$ref"));
                } else {
                    addOneOf(oneOf);
                }
            });
        }
        if (jsonSchema.containsKey("definitions")) {
            var definitions = (Map<String, Map<String, Object>>) jsonSchema.get("definitions");
            Map<String, Map<String, Object>> referencedDefinitions = new LinkedHashMap<>();
            definitions.forEach((key, value) -> {
                if (value.containsKey("oneOf") && jsonSchema.containsKey("discriminator")) {
                    // WARNING: assumes the same interface as top level schema
                    topLevelName[0] = key;
                    addDefinition("#/definitions/" + key, ClassTypeDef.of(capitalize(key)), true);
                } else if (value.containsKey("oneOf")) {
                    // inner oneOf's are treated as objects
                    addDefinition("#/definitions/" + key, TypeDef.OBJECT, true);
                } else if (value.containsKey("anyOf")) {
                    // pick first
                    var firstType = ((List<Map<String, Object>>) value.get("anyOf")).get(0);
                    addDefinition(key, firstType);
                } else if (value.containsKey("$ref")) {
                    referencedDefinitions.put(key, value);
                } else {
                    addDefinition(key, value);
                }
            });
            referencedDefinitions.forEach(DefinitionsAggregator::addDefinition);
        }

        // generate top level schema
        generateFromSchemaMap(jsonSchema, outputPath, packageName, topLevelName);
        generatedClassCount.getAndIncrement();

        // generate classes in definitions and oneOfs
        if (jsonSchema.containsKey("definitions")) {
            var definitions = (Map<String, Map<String, Object>>) jsonSchema.get("definitions");
            definitions.entrySet()
                .stream()
                .filter(definition -> {
                    // assuming single inheritance at the top level, skips any other oneOf
                    if (definition.getValue().containsKey("oneOf")) {
                        return false;
                    }
                    return Objects.requireNonNull(getDefinition("#/definitions/" + definition.getKey())).getValue();
                }).forEach(definition -> {
                    try {
                        topLevelName[0] = capitalize(getCamelCaseName(definition.getKey()));
                        generateFromSchemaMap(definition.getValue(), outputPath, packageName, topLevelName);
                        generatedClassCount.getAndIncrement();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        for (Map.Entry<String, Map<String, ?>> oneOf : getOneOfsToGenerate()) {
            topLevelName[0] = oneOf.getKey();
            generateFromSchemaMap(oneOf.getValue(), outputPath, packageName, topLevelName);
            generatedClassCount.getAndIncrement();
        }
        clearAllDefinitions();
        return generatedClassCount.get();
    }

    private File generateFromSchemaMap(Map<String, ?> jsonSchema, Path outputPath, String packageName, String[] fileName) throws IOException {
        try {
            String decidedFileName = getFileName(jsonSchema, Optional.ofNullable(fileName).map(t -> t[0]), language);
            File outputFile = getOutputFile(outputPath, packageName, decidedFileName);
            String simpleName = outputFile.getName().substring(0, outputFile.getName().lastIndexOf('.'));
            String builderClassName = packageName + "." + simpleName;

            // decide type of generated object
            ObjectType type;
            if (jsonSchema.containsKey("enum")) {
                type = ObjectType.ENUM;
            } else if (jsonSchema.containsKey("oneOf")) {
                // top level = superclass
                if (jsonSchema.containsKey("properties") || jsonSchema.containsKey("type") || jsonSchema.containsKey("allOf")) {
                    type = ObjectType.CLASS;
                } else {
                    type = ObjectType.INTERFACE;
                }
            } else if (isInheriting(simpleName)) {
                // inheriting
                type = ObjectType.CLASS;
            } else {
                type = ObjectType.RECORD;
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                ObjectDef objectDef = switch (type) {
                    case ENUM -> buildEnum(jsonSchema, builderClassName);
                    case CLASS -> buildClass(jsonSchema, builderClassName);
                    case INTERFACE -> buildInterface(jsonSchema, builderClassName);
                    default -> buildRecord(jsonSchema, builderClassName);
                };
                sourceGenerator.write(objectDef, writer);
            }

            // add definition of superclass only after generation is complete!
            if (jsonSchema.containsKey("oneOf")) {
                if (type == ObjectType.CLASS) {
                    addDefinition("superClass", ClassTypeDef.of(simpleName), true);
                } else if (type == ObjectType.INTERFACE) {
                    addDefinition("superInterface", ClassTypeDef.of(simpleName), true);
                }
            }
            return outputFile;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public EnumDef buildEnum(Map<String, ?> jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(builderClassName)
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
        // TODO: throw error on default?
        cases.put(ExpressionDef.nullValue(), ExpressionDef.nullValue());
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

        addFields(jsonSchema, objectBuilder);

        if (!discriminatorProperty.isBlank()) {
            AnnotationDef jsonTypeInfo = AnnotationDef.builder(JsonTypeInfo.class)
                .addMember("use", JsonTypeInfo.Id.NAME)
                .addMember("property", discriminatorProperty)
                .build();
            objectBuilder.addAnnotation(jsonTypeInfo);

            Map<String, Map<String, ?>> properties = (Map<String, Map<String, ?>>) jsonSchema.get("properties");
            if (properties.containsKey(discriminatorProperty)) {
                objectBuilder.addField(FieldDef.builder(discriminatorProperty)
                    .ofType(TypeDef.STRING)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .initializer(ExpressionDef.constant(properties.get(discriminatorProperty).get("const")))
                    .build());
            }
        }
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

    private void addFields(Map<String, ?> jsonSchema, ObjectDefBuilder builder) {
        if (jsonSchema.containsKey("description")) {
            builder.addJavadoc(jsonSchema.get("description").toString());
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
                addField(builder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));

            if (jsonSchema.containsKey("additionalProperties") && !Objects.equals(jsonSchema.get("additionalProperties").toString(), "false")) {
                TypeDef mapType;
                if (Objects.equals(jsonSchema.get("additionalProperties").toString(), "true")) {
                    mapType = TypeDef.OBJECT;
                } else {
                    Map<String, Object> map = (Map<String, Object>) jsonSchema.get("additionalProperties");
                    mapType = getTypeDefFromJson(map);
                }
                TypeDef type = TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING, mapType);
                builder.addProperty(PropertyDef.builder("unknownFields")
                        .ofType(type)
                        .build());
                builder.addMethod(MethodDef.builder("otherFields")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(type)
                        .addAnnotation(JsonAnyGetter.class)
                        .build((aThis, parameters) -> {
                            if (builder instanceof ClassDef.ClassDefBuilder) {
                                return aThis.field("unknownFields", type).returning();
                            }
                            return new VariableDef.Local("unknownFields", type).returning();
                        }));
                builder.addMethod(MethodDef.builder("setOtherField")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeDef.VOID)
                        .addAnnotation(JsonAnySetter.class)
                        .addParameter("name", TypeDef.STRING)
                        .addParameter("value", mapType)
                        .build((aThis, parameters) -> {
                            if (builder instanceof ClassDef.ClassDefBuilder) {
                                return aThis.field("unknownFields", type)
                                    .invoke("put", mapType, parameters);
                            }
                            return new VariableDef.Local("unknownFields", type)
                                .invoke("put", mapType, parameters);
                        }));
            }
        }
    }

    private void addField(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description, boolean isRequired) {
        if (propertyName.equals(discriminatorProperty)) {
            return;
        }
        String name = getCamelCaseName(propertyName);
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(name);
        if (!name.equals(propertyName)) {
            AnnotationDef annotationDef = AnnotationDef.builder(JsonProperty.class).addMember("value", propertyName).build();
            propertyDef.addAnnotation(annotationDef);
        }

        // add type info and type validation annotations
        TypeDef propertyType = getTypeDefFromJson(description);
        if (description.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, name, description);
        }

        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, propertyName, description);
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, TypeDef.of(List.class), isRequired);
        } else {
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        }

        // add javadoc
        if (description.containsKey("description")) {
            propertyDef.addJavadoc(description.get("description").toString());
        }

        PropertyDef property = propertyDef.build();
        // transfer to field if it is const and class builder
        if (description.containsKey("const") && objectBuilder instanceof ClassDef.ClassDefBuilder) {
            FieldDef.FieldDefBuilder fieldDefBuilder = FieldDef.builder(name)
                .ofType(property.getType())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(description.get("const")));
            property.getAnnotations().forEach(fieldDefBuilder::addAnnotation);
            property.getJavadoc().forEach(fieldDefBuilder::addJavadoc);
            ((ClassDef.ClassDefBuilder) objectBuilder).addField(fieldDefBuilder.build());
            return;
        }
        objectBuilder.addProperty(property);
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

    private TypeDef getEnumType(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        EnumDef enumDef = buildEnum(description, capitalize(propertyName));
        objectBuilder.addInnerType(enumDef);
        return enumDef.asTypeDef();
    }

    private TypeDef getListTypeDef(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        var items = (Map<String, Object>) description.get("items");
        Class listClass = List.class;
        if (description.containsKey("uniqueItems") && description.get("uniqueItems").toString().equals("true")) {
            listClass = Set.class;
        }

        TypeDef propertyType = getTypeDefFromJson(items);
        if (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, propertyName, items);
        } else if (items.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, propertyName, items);
        } else if (propertyType instanceof TypeDef.Primitive primitive) {
            propertyType = primitive.wrapperType();
        }

        var annotations = AnnotationsAggregator.getAnnotations(items, propertyType);
        return TypeDef.parameterized(listClass, propertyType.annotated(annotations));
    }
}

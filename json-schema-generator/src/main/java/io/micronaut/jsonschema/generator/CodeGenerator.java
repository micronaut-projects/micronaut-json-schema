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
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static String inputFileName = "";

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
        Schema jsonSchema = getJsonSchema(inputStream, null);
        inputFileName = "InputStream.schema.json";
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return generateFromSchemaMap(jsonSchema, outputPath, packageName, fileName);
    }

    /**
     * A method for creating a single record from a json schema.
     *
     * @param jsonFileLocation The input file location of a json schema
     * @param outputPath       The output path for the output file
     * @param packageName      The package name for the output file
     * @param fileName         The fileName for the output file
     */
    public void generate(File jsonFileLocation, Path outputPath, String packageName, String fileName) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        inputFileName = jsonFileLocation.getName();
        generateFromSchemaMap(jsonSchema, outputPath, packageName, fileName);
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param inputStream The input stream of a json schema
     * @param schemaFileName The schema file's name
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     * @return The number of generated files
     */
    public int generate(InputStream inputStream, String schemaFileName, Path outputPath, String packageName) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        inputFileName = schemaFileName;
        saveDefinitions(jsonSchema);
        int generatedCount = generateDefinitions(jsonSchema, outputPath, packageName);
        clearAllDefinitions();
        return generatedCount;
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
        inputFileName = jsonFileLocation.getName();
        saveDefinitions(jsonSchema);
        int generatedCount = generateDefinitions(jsonSchema, outputPath, packageName);
        clearAllDefinitions();
        return generatedCount;
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a json schema.
     *
     * @param jsonFolderLocation The input folder location of json schemas
     * @param outputPath The output path for the output file
     * @param packageName The package name for the output file
     */
    public void generate(Path jsonFolderLocation, Path outputPath, String packageName) {
        try {
            // TODO: optimise
            HashMap<Schema, String> schemas = new HashMap<>();
            // Walk through the directory to find all json files
            Files.walk(jsonFolderLocation)
                .filter(file -> file.toString().endsWith(".schema.json")) // Filter to only JSON schema files
                .forEach(file -> {
                    try {
                        // Read content of each JSON file
                        var jsonSchema = getJsonSchema(null, file.toFile());
                        inputFileName = file.getFileName().toString();
                        schemas.put(jsonSchema, inputFileName);
                        saveDefinitions(jsonSchema);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            schemas.forEach((jsonSchema, fileName) -> {
                    try {
                        inputFileName = fileName;
                        generateDefinitions(jsonSchema, outputPath, packageName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            clearAllDefinitions();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveDefinitions(Schema jsonSchema) throws IOException {
        String schemaName;
        if (jsonSchema.hasTitle()) {
            schemaName = capitalize(getCamelCaseName(jsonSchema.getTitle()));
        } else {
            schemaName = capitalize(getCamelCaseName(inputFileName.substring(0, inputFileName.indexOf('.'))));
        }
        addDefinition(inputFileName + "#/" + schemaName, jsonSchema);
        // save all definition and oneOf types
        if (jsonSchema.hasOneOf()) {
            jsonSchema.getOneOf().forEach(oneOf -> {
                if (oneOf.has$ref()) {
                    String ref = oneOf.get$ref();
                    if (ref.indexOf("#") == 0) {
                        ref = inputFileName + ref;
                    }
                    addOneOf(ref);
                } else {
                    addOneOf(inputFileName, oneOf);
                }
            });
        }
        if (jsonSchema.has$defs()) {
            Map<String, Schema> referencedDefinitions = new LinkedHashMap<>();
            Map<String, Schema> definitions = jsonSchema.get$defs();

            definitions.forEach((key, value) -> {
                if (value.hasOneOf() && jsonSchema.hasDiscriminator()) {
                    // WARNING: assumes the same interface as top level schema
                    addDefinition(inputFileName + "#/definitions/" + key, getDefinitionType(inputFileName + "#/" + schemaName), true);
                } else if (value.hasOneOf()) {
                    // inner oneOf's are treated as objects
                    addDefinition(inputFileName + "#/definitions/" + key, TypeDef.OBJECT, true);
                } else if (value.hasAnyOf()) {
                    // strategy: pick first
                    var firstType = value.getAnyOf().get(0);
                    addDefinition(inputFileName + "#/definitions/" + key, firstType);
                } else if (value.has$ref()) {
                    referencedDefinitions.put(inputFileName + "#/definitions/" + key, value);
                } else {
                    addDefinition(inputFileName + "#/definitions/" + key, value);
                }
            });
            referencedDefinitions.forEach(DefinitionsAggregator::addDefinition);
        }
    }

    private int generateDefinitions(Schema jsonSchema, Path outputPath, String packageName) throws IOException {
        AtomicInteger generatedClassCount = new AtomicInteger();
        // generate top level schema
        String schemaName = jsonSchema.hasTitle() ? jsonSchema.getTitle() : inputFileName.substring(0, inputFileName.indexOf('.'));
        schemaName = capitalize(getCamelCaseName(schemaName));

        generateFromSchemaMap(jsonSchema, outputPath, packageName, schemaName);
        generatedClassCount.getAndIncrement();

        // generate classes in definitions and oneOfs
        if (jsonSchema.has$defs()) {
            jsonSchema.get$defs().entrySet()
                .stream()
                .filter(definition -> {
                    // assuming single inheritance at the top level, skips any other oneOf
                    if (definition.getValue().hasOneOf()) {
                        return false;
                    }
                    return Objects.requireNonNull(getDefinition(inputFileName + "#/definitions/" + definition.getKey())).getValue();
                }).forEach(definition -> {
                    try {
                        var className = capitalize(getCamelCaseName(definition.getKey()));
                        generateFromSchemaMap(definition.getValue(), outputPath, packageName, className);
                        generatedClassCount.getAndIncrement();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        for (Map.Entry<String, Schema> oneOf : getOneOfsToGenerate()) {
            generateFromSchemaMap(oneOf.getValue(), outputPath, packageName, oneOf.getKey());
            generatedClassCount.getAndIncrement();
        }
        return generatedClassCount.get();
    }

    private File generateFromSchemaMap(Schema jsonSchema, Path outputPath, String packageName, String fileName) throws IOException {
        try {
            String decidedFileName = getFileName(jsonSchema, Optional.ofNullable(fileName), language);
            File outputFile = getOutputFile(outputPath, packageName, decidedFileName);
            String simpleName = outputFile.getName().substring(0, outputFile.getName().lastIndexOf('.'));
            String builderClassName = packageName + "." + simpleName;

            // decide type of generated object
            ObjectType type;
            if (jsonSchema.isEnum()) {
                type = ObjectType.ENUM;
            } else if (jsonSchema.hasOneOf()) {
                // top level = superclass
                // TODO add a isClass() method to schema
                if (jsonSchema.hasProperties() || jsonSchema.hasType() || jsonSchema.hasAllOf()) {
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
            if (jsonSchema.hasOneOf()) {
                if (type == ObjectType.CLASS) {
                    addDefinition(inputFileName + "/superClass", ClassTypeDef.of(simpleName), true);
                } else if (type == ObjectType.INTERFACE) {
                    addDefinition(inputFileName + "/superInterface", ClassTypeDef.of(simpleName), true);
                }
            }
            return outputFile;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public EnumDef buildEnum(Schema jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC);
        boolean isComplexEnum = false;
        LinkedHashMap<ExpressionDef.Constant, ExpressionDef> cases = new LinkedHashMap<>();
        for (Object anEnum : jsonSchema.getEnumValues()) {
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
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
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

    private RecordDef buildRecord(Schema jsonSchema, String builderClassName) throws IOException {
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private ClassDef buildClass(Schema jsonSchema, String builderClassName) throws IOException {
        ClassDef.ClassDefBuilder objectBuilder = ClassDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        if (hasDefinition(inputFileName + "/superClass")) {
            var superClass = getDefinitionType(inputFileName + "/superClass");
            objectBuilder.superclass((ClassTypeDef) superClass);
        } else if (hasDefinition(inputFileName + "/superInterface")) {
            var superInterface = getDefinitionType(inputFileName + "/superInterface");
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

            Map<String, Schema> properties = jsonSchema.getProperties();
            if (properties.containsKey(discriminatorProperty)) {
                objectBuilder.addField(FieldDef.builder(discriminatorProperty)
                    .ofType(TypeDef.STRING)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .initializer(ExpressionDef.constant(properties.get(discriminatorProperty).getConstValue()))
                    .build());
            }
        }
        return objectBuilder.build();
    }

    private InterfaceDef buildInterface(Schema jsonSchema, String builderClassName) throws IOException {
        InterfaceDef.InterfaceDefBuilder objectBuilder = InterfaceDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);
        if (jsonSchema.hasDiscriminator()) {
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

    private void addFields(Schema jsonSchema, ObjectDefBuilder builder) {
        if (jsonSchema.hasDescription()) {
            builder.addJavadoc(jsonSchema.getDescription());
        }
        if (jsonSchema.hasProperties()) {
            List<String> requiredProperties = (jsonSchema.getRequired() != null) ? jsonSchema.getRequired() : new ArrayList<>();
            jsonSchema.getProperties().forEach((key, value) -> addField(
                builder,
                key,
                value,
                requiredProperties.contains(key)
            ));

            if (jsonSchema.hasAdditionalProperties() && !jsonSchema.getAdditionalProperties().equals(Schema.FALSE)) {
                TypeDef mapType;
                if (jsonSchema.getAdditionalProperties().equals(Schema.TRUE)) {
                    mapType = TypeDef.OBJECT;
                } else {
                    mapType = getTypeDefFromJson(jsonSchema.getAdditionalProperties());
                }
                TypeDef type = TypeDef.parameterized(ClassTypeDef.of(HashMap.class), TypeDef.STRING, mapType);
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
                            var unknownField = (builder instanceof ClassDef.ClassDefBuilder) ?
                                aThis.field("unknownFields", type) :
                                new VariableDef.Local("unknownFields", type);

                            return StatementDef.multi(
                                unknownField.isNull().asConditionIf(unknownField.assign(ClassTypeDef.of(HashMap.class).instantiate())),
                                unknownField.invoke("put", mapType, parameters));
                        }));
            }
        }
    }

    private void addField(ObjectDefBuilder objectBuilder, String propertyName, Schema schema, boolean isRequired) {
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
        TypeDef propertyType = getTypeDefFromJson(schema);
        if (schema.isEnum()) {
            propertyType = getEnumType(objectBuilder, name, schema);
        }

        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, propertyName, schema);
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, schema, TypeDef.of(List.class), isRequired);
        } else {
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, schema, propertyType, isRequired);
        }

        // add javadoc
        if (schema.hasDescription()) {
            propertyDef.addJavadoc(schema.getDescription());
        }

        PropertyDef property = propertyDef.build();
        // transfer to field if it is const and class builder
        if (schema.hasConstValue() && objectBuilder instanceof ClassDef.ClassDefBuilder) {
            FieldDef.FieldDefBuilder fieldDefBuilder = FieldDef.builder(name)
                .ofType(property.getType())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(schema.getConstValue()));
            property.getAnnotations().forEach(fieldDefBuilder::addAnnotation);
            property.getJavadoc().forEach(fieldDefBuilder::addJavadoc);
            ((ClassDef.ClassDefBuilder) objectBuilder).addField(fieldDefBuilder.build());
            return;
        }
        objectBuilder.addProperty(property);
    }

    private void addDiscriminatorAnnotations(Schema jsonSchema, ObjectDefBuilder objectBuilder) {
        if (!jsonSchema.hasDiscriminator()) {
            return;
        }
        var discriminator = jsonSchema.getDiscriminator();
        discriminatorProperty = discriminator.getPropertyName();

        List<AnnotationDef> subTypeList = discriminator.getMapping().entrySet()
            .stream()
            .map(entry -> AnnotationDef
                .builder(JsonSubTypes.Type.class)
                .addMember("value", getDefinitionType(inputFileName + entry.getValue()))
                .addMember("name", entry.getKey())
                .build())
            .toList();
        AnnotationDef jsonSubTypes = AnnotationDef.builder(JsonSubTypes.class).addMember("value", subTypeList).build();
        objectBuilder.addAnnotation(jsonSubTypes);
    }

    private TypeDef getEnumType(ObjectDefBuilder objectBuilder, String propertyName, Schema schema) {
        EnumDef enumDef = buildEnum(schema, capitalize(propertyName));
        objectBuilder.addInnerType(enumDef);
        return enumDef.asTypeDef();
    }

    private TypeDef getListTypeDef(ObjectDefBuilder objectBuilder, String propertyName, Schema schema) {
        Schema items = schema.getItems() != null ? schema.getItems() : schema.getContains();
        if (items == null) {
            return TypeDef.OBJECT;
        }

        TypeDef propertyType = getTypeDefFromJson(items);
        if (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, propertyName, items);
        } else if (items.isEnum()) {
            propertyType = getEnumType(objectBuilder, propertyName, items);
        } else if (propertyType instanceof TypeDef.Primitive primitive) {
            propertyType = primitive.wrapperType();
        }

        var annotations = AnnotationsAggregator.getAnnotations(items, propertyType);
        return TypeDef.parameterized(
            (schema.isUniqueItems() != null && schema.isUniqueItems()) ? Set.class : List.class,
            propertyType.annotated(annotations));
    }

    public static String getInputFileName() {
        return inputFileName;
    }
}

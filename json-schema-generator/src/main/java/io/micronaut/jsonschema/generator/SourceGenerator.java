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
import io.micronaut.jsonschema.generator.loaders.FileLoader;
import io.micronaut.jsonschema.generator.loaders.FileProcessor;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.loaders.FileProcessor.getJsonSchema;
import static io.micronaut.jsonschema.generator.loaders.FileProcessor.getOutputFile;
import static io.micronaut.jsonschema.generator.utils.GeneratorContext.*;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.*;
import static io.micronaut.jsonschema.model.Schema.DEF_SCHEMA_REF_PREFIX;

/**
 * A source generator to create source files from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.3
 */
@Internal
public final class SourceGenerator {

    private static String inputFileName = null;
    private static VisitorContext.Language language;
    private static Path outputPath;
    private static String outputPackageName;

    private enum ObjectType { CLASS, RECORD, INTERFACE, ENUM }
    private final io.micronaut.sourcegen.generator.SourceGenerator sourceGenerator;
    private String discriminatorProperty = "";

    /**
     * Constructs a new {@link SourceGenerator} instance based on the provided programming language.
     * <p>
     * This constructor attempts to find a corresponding {@link io.micronaut.sourcegen.generator.SourceGenerator} implementation
     * for the given programming language. If no such implementation is found, a {@link RuntimeException}
     * is thrown.
     * </p>
     *
     * @param language The {@link VisitorContext.Language} representing the target programming language
     *                 for which the source generator is to be created. This argument cannot be {@code null}.
     * @throws RuntimeException if no matching source generator is found for the provided language.
     *                          The exception message will indicate the language for which no generator was found.
     */
    public SourceGenerator(VisitorContext.Language language) {
        sourceGenerator = SourceGenerators.findByLanguage(language).orElse(null);
        if (sourceGenerator == null) {
            throw new RuntimeException("No source generator found for language " + language);
        }
        SourceGenerator.language = language;
    }

    /**
     * Generates source code from JSON schema files based on the provided configuration.
     * <p>
     * This method first checks if an {@code inputFolder} is specified in the configuration. If the
     * {@code inputFolder} is provided, it processes all JSON Schema in the folder to generate source code.
     * If {@code inputFolder} is {@code null}, it attempts to retrieve the JSON schema from the specified
     * {@code inputStream}, {@code jsonUrl}, or {@code jsonFile} in the configuration and generates code from the schema.
     * </p>
     * <p>
     * If the {@code outputFileName} exists, the method generates a single source file from the schema with that file name.
     * Otherwise, the method generates all objects defined in the schema inside the specified {@code outputPath}
     * (and {@code outputPackageName} if available).
     * </p>
     *
     * @param config The {@link SourceGeneratorConfig} object that contains the configuration for source code generation,
     *               including input folder, JSON schema URL, output path, output package name, and output file name.
     * @return The top level schema's generated File when a single input is given, null otherwise.
     * @throws IOException If an I/O error occurs during file or directory creation, or if an error occurs while reading or writing files.
     */
    public File generate(SourceGeneratorConfig config) throws IOException {
        outputPath = config.outputPath();
        outputPackageName = config.outputPackageName();
        if (config.inputFolder() != null) {
            generateFolder(config);
        } else {
            Schema jsonSchema = getJsonSchema(config);
            assert jsonSchema != null;
            inputFileName = getInputFileName() != null ? getInputFileName() : config.getInputName();
            // single java object is generated
            if (config.outputFileName() != null && !config.outputFileName().isBlank()) {
                var outputFileName = config.outputFileName();
                // remove extension from file name if there is
                if (config.outputFileName().contains(".")) {
                    outputFileName = outputFileName.substring(0, outputFileName.indexOf('.'));
                }
                return generateFromSchema(jsonSchema, config.outputPath(), config.outputPackageName(), outputFileName);
            } else {
                saveDefinitions(jsonSchema);
                return generateDefinitions(jsonSchema, config.outputPath(), config.outputPackageName());
            }
        }
        return null;
    }

    /**
     * A method for creating multiple objects (class, record, interface) from a folder of JSON Schema.
     *
     * @param config     The SourceGeneratorConfig
     */
    private void generateFolder(SourceGeneratorConfig config) throws IOException {
        HashMap<Schema, String> schemas = new HashMap<>();
        Path jsonFolder =  config.inputFolder();
        // Walk through the directory to find all json files
        try (Stream<Path> paths = Files.walk(jsonFolder).filter(file -> file.toString().endsWith(".schema.json"))) {
            paths.forEach(path -> {
                // Read content of each JSON file
                var jsonSchema = new FileLoader(path.toFile()).load();
                assert jsonSchema != null;
                inputFileName = path.toString().substring(jsonFolder.toString().length() + 1);
                schemas.put(jsonSchema, inputFileName);
                saveDefinitions(jsonSchema);
            });
        } catch (IOException e) {
            throw new FileSystemException(jsonFolder.toString());
        }

        schemas.forEach((jsonSchema, fileName) -> {
            try {
                inputFileName = fileName;
                generateDefinitions(jsonSchema, config.outputPath(), config.outputPackageName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void saveDefinitions(Schema jsonSchema) {
        String schemaName = jsonSchema.hasTitle() ? jsonSchema.getTitle() : inputFileName.substring(0, inputFileName.indexOf('.'));
        String finalSchemaName = capitalize(getCamelCaseName(schemaName));

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
                    addOneOf(oneOf);
                }
            });
        }
        if (jsonSchema.has$defs()) {
            jsonSchema.get$defs().forEach((key, value) -> {
                if (key.equals("//")) {
                    if (!jsonSchema.hasDescription()) {
                        jsonSchema.setDescription(String.valueOf(value));
                    } else {
                        jsonSchema.setDescription(jsonSchema.getDescription() + "<br>" + value);
                    }
                } else if (value.hasOneOf() && jsonSchema.hasDiscriminator()) {
                    // WARNING: assumes the same interface as top level schema
                    addDefinition(inputFileName + DEF_SCHEMA_REF_PREFIX + key, TypeDef.THIS, true);
                } else {
                    addDefinition(inputFileName + DEF_SCHEMA_REF_PREFIX + key, value);
                }
            });
        }
        addDefinition(inputFileName + "#/" + finalSchemaName, jsonSchema);
    }

    private File generateDefinitions(Schema jsonSchema, Path outputPath, String packageName) throws IOException {
        // generate top level schema
        String schemaName = jsonSchema.hasTitle() ? jsonSchema.getTitle() : inputFileName.substring(0, inputFileName.indexOf('.'));
        schemaName = capitalize(getCamelCaseName(schemaName));

        File topLevelObject = generateFromSchema(jsonSchema, outputPath, packageName, schemaName);

        // generate classes in definitions and oneOfs
        if (jsonSchema.has$defs()) {
            jsonSchema.get$defs().entrySet()
                .stream()
                .filter(definition -> !definition.getKey().equals("//") && isDefinitionClass(inputFileName + DEF_SCHEMA_REF_PREFIX + definition.getKey()))
                .forEach(definition -> {
                    try {
                        var className = capitalize(getCamelCaseName(definition.getKey()));
                        generateFromSchema(definition.getValue(), outputPath, packageName, className);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        for (Map.Entry<String, Schema> oneOf : getOneOfsToGenerate()) {
            String className = oneOf.getKey().substring(oneOf.getKey().lastIndexOf('/') + 1);
            generateFromSchema(oneOf.getValue(), outputPath, packageName, className);
        }
        return topLevelObject;
    }

    private File generateFromSchema(Schema jsonSchema, Path outputPath, String packageName, String fileName) throws IOException {
        try {
            String decidedFileName = getFileName(jsonSchema, Optional.ofNullable(fileName));
            String simpleName = decidedFileName.substring(0, decidedFileName.lastIndexOf('.'));

            // decide type of generated object
            boolean hasOverLimitParameters = jsonSchema.hasProperties() && jsonSchema.getProperties().size() > 255;
            ObjectType type;
            if (jsonSchema.isEnum()) {
                type = ObjectType.ENUM;
            } else if (jsonSchema.hasOneOf()) {
                // top level -> superclass
                if (jsonSchema.hasProperties() || jsonSchema.hasType() || jsonSchema.hasAllOf()) {
                    type = ObjectType.CLASS;
                } else {
                    type = ObjectType.INTERFACE;
                }
            } else if (isInheriting(simpleName) || hasOverLimitParameters) {
                type = ObjectType.CLASS;
            } else if (getTypeDefFromJson(jsonSchema).equals(TypeDef.OBJECT)) {
                type = ObjectType.RECORD;
            } else {
                return null;
            }

            File outputFile = getOutputFile(outputPath, packageName, decidedFileName);
            String builderClassName = packageName + "." + simpleName;
            try (FileWriter writer = new FileWriter(outputFile)) {
                ObjectDef objectDef = switch (type) {
                    case ENUM -> buildEnum(jsonSchema, builderClassName);
                    case CLASS -> buildClass(jsonSchema, builderClassName);
                    case INTERFACE -> buildInterface(jsonSchema, builderClassName);
                    case RECORD -> buildRecord(jsonSchema, builderClassName);
                    default -> throw new IllegalStateException("Unexpected enum type: " + type);
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
        LinkedHashMap<String, String> enumValues = new LinkedHashMap<>();
        for (Object anEnum : jsonSchema.getEnumValues()) {
            String enumConst = anEnum.toString();
            String constName;
            if (isOnlyLetters(enumConst)) {
                constName = getConstantName(enumConst);
            } else {
                constName = unicodeToString(enumConst);
            }
            enumValues.put(constName, enumConst);
            if (!constName.equals(enumConst)) {
                isComplexEnum = true;
            }
        }
        boolean finalIsComplexEnum = isComplexEnum;
        enumValues.forEach((constName, enumConst) -> {
            if (!finalIsComplexEnum) {
                enumBuilder.addEnumConstant(constName);
            } else {
                enumBuilder.addEnumConstant(constName, ExpressionDef.constant(enumConst));
                cases.put(ExpressionDef.constant(enumConst), new VariableDef.Constant(TypeDef.THIS, constName));
            }
        });

        if (isComplexEnum) {
            // cases.put(ExpressionDef.nullValue(), ExpressionDef.nullValue());
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
                        parameters.get(0).asExpressionSwitch(TypeDef.STRING, cases, ExpressionDef.nullValue()).returning()
                    ));
        }
        addFields(jsonSchema, enumBuilder);
        return enumBuilder.build();
    }

    private RecordDef buildRecord(Schema jsonSchema, String builderClassName) {
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private ClassDef buildClass(Schema jsonSchema, String builderClassName) {
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

    private InterfaceDef buildInterface(Schema jsonSchema, String builderClassName) {
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
            builder.addJavadoc(getJavadoc(jsonSchema.getDescription()));
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
        AnnotationsAggregator.addAnnotations(propertyDef, schema, propertyType, isRequired);
        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, name, schema);
        }
        if (propertyType.equals(TypeDef.OBJECT) && schema.hasProperties()) {
            // inner type
            ObjectDef builder;
            if (schema.getProperties().size() > 255 || schema.hasAdditionalProperties() || schema.hasConstValue()) {
                builder = buildClass(schema, capitalize(name));
            } else {
                builder = buildRecord(schema, capitalize(name));
            }
            objectBuilder.addInnerType(builder);
            propertyType = ClassTypeDef.of(builder.getName());
        }
        propertyDef.ofType(propertyType);

        // add javadoc
        if (schema.hasDescription()) {
            propertyDef.addJavadoc(getJavadoc(schema.getDescription()));
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

    private String getJavadoc(String description) {
        if (description.isBlank()) {
            return "";
        }
        return description
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("&", "&amp;")
            .replaceAll("'", "&apos;")
            .replaceAll("\"", "&quot;")
            .replaceAll("\n", "<br>")
            .trim();
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

    public static void setInputFileName(String inputFileName) {
        SourceGenerator.inputFileName = inputFileName;
    }

    public static List<String> getAllowedUrlPatterns() {
        return FileProcessor.getAllowedUrlPatterns();
    }

    public static void setAllowedUrlPatterns(List<String> allowedUrlPatterns) {
        FileProcessor.setAllowedUrlPatterns(allowedUrlPatterns);
    }

    public static Path getOutputPath() {
        return outputPath;
    }

    public static String getOutputPackageName() {
        return outputPackageName;
    }

    public static VisitorContext.Language getLanguage() {
        return language;
    }
}

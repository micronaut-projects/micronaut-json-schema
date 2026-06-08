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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
import io.micronaut.jsonschema.generator.loaders.FileLoader;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.RecordAdoptionStrategy;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.EnumDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ObjectDef;
import io.micronaut.sourcegen.model.ObjectDefBuilder;
import io.micronaut.sourcegen.model.PropertyDef;
import io.micronaut.sourcegen.model.RecordDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.JSON_ANY_GETTER_ANN;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.JSON_ANY_SETTER_ANN;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.JSON_CREATOR_ANN;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.JSON_VALUE_ANN;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.SERDEABLE_ANN;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.getJsonPropertyAnn;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.getJsonSubTypesAnn;
import static io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator.getJsonTypeInfoAnn;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.TYPE_MAP_NULLABLE;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getClassName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getConstantName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getPropertyName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.isOnlyLetters;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.unicodeToString;
import static io.micronaut.jsonschema.generator.loaders.FileProcessor.getFileName;
import static io.micronaut.jsonschema.generator.loaders.FileProcessor.getJsonSchema;
import static io.micronaut.jsonschema.generator.loaders.FileProcessor.getOutputFile;
import static io.micronaut.jsonschema.model.Schema.DEF_SCHEMA_REF_PREFIX;

/**
 * A source generator to create source files from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.3
 */
@Internal
public final class SourceGenerator {

    private static @Nullable String inputFileName = null;
    private static VisitorContext.@Nullable Language language;
    private static @Nullable Path outputPath;
    private static @Nullable String outputPackageName;

    private enum ObjectType { CLASS, RECORD, INTERFACE, ENUM }
    private final io.micronaut.sourcegen.generator.SourceGenerator sourceGenerator;
    private final GeneratorContext context;
    private String discriminatorProperty = "";

    /**
     * Constructs a new {@link SourceGenerator} instance based on the provided programming language.
     * <p>
     * This constructor attempts to find a corresponding {@link io.micronaut.sourcegen.generator.SourceGenerator} implementation
     * for the given programming language. If no such implementation is found, a {@link RuntimeException}
     * is thrown.
     * </p>
     *
     * @param lang The String representing the target programming language
     *                 for which the source generator is to be created. This argument cannot be {@code null}.
     * @throws RuntimeException if no matching source generator is found for the provided language.
     *                          The exception message will indicate the language for which no generator was found.
     */
    public SourceGenerator(String lang) {
        this(VisitorContext.Language.valueOf(lang.toUpperCase()), new GeneratorContext());
    }

    /**
     * Constructs a new {@link SourceGenerator} instance based on the provided programming language and the generation context.
     * <p>
     * This constructor attempts to find a corresponding {@link io.micronaut.sourcegen.generator.SourceGenerator} implementation
     * for the given programming language. If no such implementation is found, a {@link RuntimeException}
     * is thrown.
     * </p>
     *
     * @param language The {@link VisitorContext.Language} representing the target programming language
     *                 for which the source generator is to be created. This argument cannot be {@code null}.
     * @param context The {@link GeneratorContext} representing the already existing context of generation.
     * @throws RuntimeException if no matching source generator is found for the provided language.
     *                          The exception message will indicate the language for which no generator was found.
     */
    public SourceGenerator(VisitorContext.Language language, GeneratorContext context) {
        sourceGenerator = SourceGenerators.findByLanguage(language)
            .orElseThrow(() -> new RuntimeException("No source generator found for language " + language));
        SourceGenerator.language = language;
        this.context = context;
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
    @Nullable
    public File generate(SourceGeneratorConfig config) throws IOException {
        context.setConfiguration(config);
        Path configOutputPath = Objects.requireNonNull(config.outputPath(), "Source generator outputPath is required");
        outputPath = configOutputPath;
        outputPackageName = config.outputPackageName();
        if (config.inputFolder() != null) {
            generateFolder(config);
        } else {
            Schema jsonSchema = getJsonSchema(config);
            assert jsonSchema != null;
            String currentInputFileName = getInputFileName();
            inputFileName = currentInputFileName != null ? currentInputFileName : config.getInputName();
            // single java object is generated
            if (config.outputFileName() != null && !config.outputFileName().isBlank()) {
                var outputFileName = config.outputFileName();
                // remove extension from file name if there is
                if (config.outputFileName().contains(".")) {
                    outputFileName = outputFileName.substring(0, outputFileName.indexOf('.'));
                }
                return generateFromSchema(jsonSchema, configOutputPath, config.outputPackageName(), outputFileName);
            } else {
                saveDefinitions(jsonSchema);
                return generateDefinitions(jsonSchema, configOutputPath, config.outputPackageName());
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
        Path jsonFolder =  Objects.requireNonNull(config.inputFolder());
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

        Path configOutputPath = Objects.requireNonNull(config.outputPath(), "Source generator outputPath is required");
        schemas.forEach((jsonSchema, fileName) -> {
            try {
                inputFileName = fileName;
                generateDefinitions(jsonSchema, configOutputPath, config.outputPackageName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void saveDefinitions(Schema jsonSchema) {
        String currentInputFileName = Objects.requireNonNull(inputFileName);
        String title = jsonSchema.getTitle();
        String schemaName = title != null ? title : currentInputFileName.substring(0, currentInputFileName.indexOf('.'));
        String finalSchemaName = getClassName(schemaName);

        // save all definition and oneOf types
        List<Schema> oneOfs = jsonSchema.getOneOf();
        if (oneOfs != null) {
            oneOfs.forEach(oneOf -> {
                if (oneOf.has$ref()) {
                    String ref = Objects.requireNonNull(oneOf.get$ref());
                    if (ref.indexOf("#") == 0) {
                        ref = currentInputFileName + ref;
                    }
                    context.addOneOf(ref);
                } else {
                    context.addOneOf(oneOf);
                }
            });
        }
        Map<String, Schema> defs = jsonSchema.get$defs();
        if (defs != null) {
            defs.forEach((key, value) -> {
                if (key.equals("//")) {
                    if (!jsonSchema.hasDescription()) {
                        jsonSchema.setDescription(String.valueOf(value));
                    } else {
                        String description = Objects.requireNonNull(jsonSchema.getDescription());
                        jsonSchema.setDescription(description + "<br>" + value);
                    }
                } else if (value.hasOneOf() && jsonSchema.hasDiscriminator()) {
                    // WARNING: assumes the same interface as top level schema
                    context.addDefinition(currentInputFileName + DEF_SCHEMA_REF_PREFIX + key, TypeDef.THIS, true);
                } else {
                    context.addDefinition(currentInputFileName + DEF_SCHEMA_REF_PREFIX + key, value);
                }
            });
        }
        context.addDefinition(currentInputFileName + "#/" + finalSchemaName, jsonSchema);
    }

    @Nullable
    private File generateDefinitions(Schema jsonSchema, Path outputPath, String packageName) throws IOException {
        // generate top level schema
        String currentInputFileName = Objects.requireNonNull(inputFileName);
        String title = jsonSchema.getTitle();
        String schemaName = title != null ? title : currentInputFileName.substring(0, currentInputFileName.indexOf('.'));
        schemaName = getClassName(schemaName);

        File topLevelObject = generateFromSchema(jsonSchema, outputPath, packageName, schemaName);

        // generate classes in definitions and oneOfs
        Map<String, Schema> defs = jsonSchema.get$defs();
        if (defs != null) {
            defs.entrySet()
                .stream()
                .filter(definition -> !definition.getKey().equals("//") && context.isDefinitionClass(currentInputFileName + DEF_SCHEMA_REF_PREFIX + definition.getKey()))
                .forEach(definition -> {
                    try {
                        var className = getClassName(definition.getKey());
                        generateFromSchema(definition.getValue(), outputPath, packageName, className);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        for (Map.Entry<String, Schema> oneOf : context.getOneOfsToGenerate()) {
            String className = oneOf.getKey().substring(oneOf.getKey().lastIndexOf('/') + 1);
            generateFromSchema(oneOf.getValue(), outputPath, packageName, className);
        }
        return topLevelObject;
    }

    @Nullable
    private File generateFromSchema(Schema jsonSchema, Path outputPath, String packageName, String fileName) throws IOException {
        try {
            String decidedFileName = getFileName(jsonSchema, Optional.ofNullable(fileName));
            String simpleName = decidedFileName.substring(0, decidedFileName.lastIndexOf('.'));

            // decide type of generated object
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
            } else if (context.isInheriting(simpleName) || shouldBeAClass(jsonSchema)) {
                type = ObjectType.CLASS;
            } else if (getTypeDefFromJson(jsonSchema, context).equals(TypeDef.OBJECT)) {
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
                    context.addDefinition(inputFileName + "/superClass", ClassTypeDef.of(simpleName), true);
                } else if (type == ObjectType.INTERFACE) {
                    context.addDefinition(inputFileName + "/superInterface", ClassTypeDef.of(simpleName), true);
                }
            }
            return outputFile;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public EnumDef buildEnum(Schema jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));
        boolean isComplexEnum = false;
        LinkedHashMap<ExpressionDef.Constant, ExpressionDef> cases = new LinkedHashMap<>();
        LinkedHashMap<String, Object> enumValues = new LinkedHashMap<>();
        int counter = 0; // for naming same const's
        List<Object> schemaEnumValues = Objects.requireNonNull(jsonSchema.getEnumValues());
        for (Object anEnum : schemaEnumValues) {
            String enumConst = anEnum.toString();
            String constName;
            if (isOnlyLetters(enumConst)) {
                constName = getConstantName(enumConst);
            } else {
                constName = unicodeToString(enumConst);
            }
            // an all number constants can have the same unicode name
            if (enumValues.containsKey(constName)) {
                ++counter;
                constName += "_" + counter;
            }
            enumValues.put(constName, anEnum);
            if (!constName.equals(anEnum)) {
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
            // check for default value
            ExpressionDef defaultValue;
            if (jsonSchema.hasDefaultValue() && enumValues.containsValue(jsonSchema.getDefaultValue())) {
                defaultValue = Objects.requireNonNull(cases.get(ExpressionDef.constant(jsonSchema.getDefaultValue())));
            } else {
                defaultValue = ExpressionDef.nullValue();
            }
            // get enum value TypeDef
            List<Schema.Type> schemaTypes = jsonSchema.getType();
            Schema.Type valueType = schemaTypes != null && !schemaTypes.isEmpty() ? schemaTypes.get(0) : Schema.Type.STRING;
            if (valueType.equals(Schema.Type.NULL)) {
                valueType = Schema.Type.STRING;
            }
            TypeDef valueTypeDef = Objects.requireNonNull(TYPE_MAP_NULLABLE.get(valueType.toString().toLowerCase(Locale.ENGLISH)));
            // add constructor field and methods
            enumBuilder.addField(FieldDef.builder("value")
                    .ofType(valueTypeDef)
                    .addModifiers(Modifier.PUBLIC)
                    .build())
                .addAllFieldsConstructor(Modifier.PRIVATE)
                .addMethod(MethodDef.builder("getValue")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(ClassTypeDef.of(JSON_VALUE_ANN))
                    .returns(valueTypeDef)
                    .build((aThis, parameters) ->
                        aThis.field("value", valueTypeDef).returning()))
                .addMethod(MethodDef.builder("statusOf")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addAnnotation(ClassTypeDef.of(JSON_CREATOR_ANN))
                    .returns(TypeDef.THIS)
                    .addParameter("value", valueTypeDef)
                    .build((aThis, parameters) ->
                        parameters.get(0).asExpressionSwitch(TypeDef.STRING, cases, defaultValue).returning()
                    ));
        }
        addFields(jsonSchema, enumBuilder);
        return enumBuilder.build();
    }

    private RecordDef buildRecord(Schema jsonSchema, String builderClassName) {
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private ClassDef buildClass(Schema jsonSchema, String builderClassName) {
        ClassDef.ClassDefBuilder objectBuilder = ClassDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));

        if (context.hasDefinition(inputFileName + "/superClass")) {
            var superClass = context.getDefinitionType(inputFileName + "/superClass");
            objectBuilder.superclass((ClassTypeDef) superClass);
        } else if (context.hasDefinition(inputFileName + "/superInterface")) {
            var superInterface = context.getDefinitionType(inputFileName + "/superInterface");
            objectBuilder.addSuperinterface(superInterface);
        } else {
            // top level class
            addDiscriminatorAnnotations(jsonSchema, objectBuilder);
        }

        addFields(jsonSchema, objectBuilder);

        if (!discriminatorProperty.isBlank()) {
            objectBuilder.addAnnotation(getJsonTypeInfoAnn(discriminatorProperty));

            Map<String, Schema> properties = Objects.requireNonNull(jsonSchema.getProperties());
            if (properties.containsKey(discriminatorProperty)) {
                Object discriminatorValue = Objects.requireNonNull(properties.get(discriminatorProperty)).getConstValue();
                objectBuilder.addField(
                    FieldDef.builder(discriminatorProperty)
                        .ofType(TypeDef.STRING)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .initializer(ExpressionDef.constant(discriminatorValue))
                        .build());
            }
        }
        return objectBuilder.build();
    }

    private InterfaceDef buildInterface(Schema jsonSchema, String builderClassName) {
        InterfaceDef.InterfaceDefBuilder objectBuilder = InterfaceDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));
        if (jsonSchema.hasDiscriminator()) {
            // top level interface
            addDiscriminatorAnnotations(jsonSchema, objectBuilder);
            if (!discriminatorProperty.isBlank()) {
                objectBuilder.addAnnotation(getJsonTypeInfoAnn(discriminatorProperty));
            }
        }
        return objectBuilder.build();
    }

    private void addFields(Schema jsonSchema, ObjectDefBuilder builder) {
        String description = jsonSchema.getDescription();
        if (description != null) {
            builder.addJavadoc(getJavadoc(description));
        }

        Map<String, Schema> properties = jsonSchema.getProperties();
        if (properties != null) {
            List<String> requiredProperties = (jsonSchema.getRequired() != null) ? jsonSchema.getRequired() : new ArrayList<>();
            properties.forEach((key, value) -> addField(
                builder,
                key,
                value,
                requiredProperties.contains(key)
            ));

            Schema additionalProperties = jsonSchema.getAdditionalProperties();
            if (additionalProperties != null && !additionalProperties.equals(Schema.FALSE)) {
                addAdditionalField(jsonSchema, builder);
            }
        }
    }

    private void addAdditionalField(Schema jsonSchema, ObjectDefBuilder builder) {
        TypeDef mapType;
        Schema additionalProperties = Objects.requireNonNull(jsonSchema.getAdditionalProperties());
        if (additionalProperties.equals(Schema.TRUE)) {
            mapType = TypeDef.OBJECT;
        } else {
            mapType = getTypeDefFromJson(additionalProperties, context);
        }
        TypeDef type = TypeDef.parameterized(ClassTypeDef.of(HashMap.class), TypeDef.STRING, mapType);
        if (builder instanceof ClassDef.ClassDefBuilder classDefBuilder) {
            classDefBuilder.addField(FieldDef.builder("unknownFields")
                .ofType(type)
                .build());
        } else {
            builder.addProperty(PropertyDef.builder("unknownFields")
                .ofType(type)
                .build());
        }
        builder.addMethod(MethodDef.builder("getUnknownFields")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addAnnotation(ClassTypeDef.of(JSON_ANY_GETTER_ANN))
                .build((aThis, parameters) -> {
                    if (builder instanceof ClassDef.ClassDefBuilder) {
                        return aThis.field("unknownFields", type).returning();
                    }
                    return new VariableDef.Local("unknownFields", type).returning();
                }));
        builder.addMethod(MethodDef.builder("setUnknownFields")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.VOID)
                .addAnnotation(ClassTypeDef.of(JSON_ANY_SETTER_ANN))
                .addParameter("name", TypeDef.STRING)
                .addParameter("value", mapType)
                .build((aThis, parameters) -> {
                    var unknownField = (builder instanceof ClassDef.ClassDefBuilder) ?
                        aThis.field("unknownFields", type) :
                        new VariableDef.Local("unknownFields", type);

                    return StatementDef.multi(
                        unknownField.ifNull(unknownField.assign(ClassTypeDef.of(HashMap.class).instantiate())),
                        unknownField.invoke("put", mapType, parameters));
                }));
    }

    private void addField(ObjectDefBuilder objectBuilder, String propertyName, Schema schema, boolean isRequired) {
        if (propertyName.equals(discriminatorProperty)) {
            return;
        }
        String name = getPropertyName(propertyName);
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(name)
            .addModifiers(Modifier.PUBLIC);
        if (!name.equals(propertyName)) {
            propertyDef.addAnnotation(getJsonPropertyAnn(propertyName));
        }

        TypeDef propertyType = getPropertyType(objectBuilder, schema, name);
        // add annotations
        var annotations = AnnotationsAggregator.getAnnotations(schema, propertyType, isRequired);
        if (!annotations.isEmpty()) {
            propertyType = propertyType.annotated(annotations);
        }
        propertyDef.ofType(propertyType);

        // add javadoc
        if (schema.hasDescription()) {
            String description = schema.getDescription();
            if (description != null) {
                propertyDef.addJavadoc(getJavadoc(description));
            }
        }

        PropertyDef property = propertyDef.build();
        // transfer to field if it is const and class builder

        if (schema.hasConstValue() && objectBuilder instanceof ClassDef.ClassDefBuilder classDefBuilder) {
            final String fieldName = name;
            final TypeDef fieldType = propertyType;
            FieldDef.FieldDefBuilder fieldDefBuilder = FieldDef.builder(fieldName)
                .ofType(fieldType)
                .addModifiers(Modifier.PRIVATE)
                .initializer(ExpressionDef.constant(schema.getConstValue()));
            property.getAnnotations().forEach(fieldDefBuilder::addAnnotation);
            property.getJavadoc().forEach(fieldDefBuilder::addJavadoc);
            FieldDef fieldDef = fieldDefBuilder.build();
            classDefBuilder.addField(fieldDef);

            // add getter
            classDefBuilder.addMethod(MethodDef.builder("get" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType)
                .build((aThis, params) -> aThis.field(fieldName, fieldType).returning()));

            // add setter
            classDefBuilder.addMethod(MethodDef.builder("set" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.VOID)
                .addParameter(fieldName, fieldType)
                .build((aThis, params) -> aThis.field(fieldName, fieldType).assign(params.get(0))));

            return;
        }
        objectBuilder.addProperty(property);
    }

    private TypeDef getPropertyType(ObjectDefBuilder objectBuilder, Schema schema, String name) {
        // add type info and type validation annotations
        TypeDef propertyType = getTypeDefFromJson(schema, context);
        if (schema.isEnum()) {
            propertyType = getEnumType(objectBuilder, name, schema);
        } else if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, name, schema);
        } else if (propertyType.equals(TypeDef.OBJECT) && schema.hasProperties()) {
            propertyType = buildInnerType(objectBuilder, name, schema);
        } else if (propertyType.equals(TypeDef.OBJECT) && schema.hasAdditionalProperties()) {
            Schema additionalProperties = Objects.requireNonNull(schema.getAdditionalProperties());
            if (additionalProperties.equals(Schema.TRUE)) {
                return TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING, TypeDef.OBJECT);
            } else {
                return TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING,
                    getPropertyType(objectBuilder, additionalProperties, name + "Item"));
            }
        }
        return propertyType;
    }

    private void addDiscriminatorAnnotations(Schema jsonSchema, ObjectDefBuilder objectBuilder) {
        if (!jsonSchema.hasDiscriminator()) {
            return;
        }
        var discriminator = Objects.requireNonNull(jsonSchema.getDiscriminator());
        discriminatorProperty = discriminator.propertyName();
        objectBuilder.addAnnotation(getJsonSubTypesAnn(discriminator.mapping(), context));
    }

    private String getJavadoc(String description) {
        if (!context.getConfiguration().javadoc().replaceHTML()) {
            return description;
        }
        if (description.isBlank()) {
            return "";
        }
        return description
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>\n")
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

        TypeDef propertyType = getPropertyType(objectBuilder, items, propertyName);
        if (propertyType instanceof TypeDef.Primitive primitive) {
            propertyType = primitive.wrapperType();
        }
        var annotations = AnnotationsAggregator.getAnnotations(items, propertyType, false);
        return TypeDef.parameterized(
            (schema.isUniqueItems() != null && schema.isUniqueItems()) ? Set.class : List.class,
            propertyType.annotated(annotations));
    }

    private TypeDef buildInnerType(ObjectDefBuilder objectBuilder, String propertyName, Schema schema) {
        // inner type
        ObjectDef builder;
        if (shouldBeAClass(schema)) {
            builder = buildClass(schema, capitalize(propertyName));
        } else {
            builder = buildRecord(schema, capitalize(propertyName));
        }
        objectBuilder.addInnerType(builder);
        return ClassTypeDef.of(builder.getName());
    }

    private boolean shouldBeAClass(Schema schema) {
        if (context.getConfiguration().recordAdoptionStrategy() == RecordAdoptionStrategy.ALWAYS_CLASS) {
            return true;
        }
        boolean hasOverLimitParameters = schema.hasProperties() && Objects.requireNonNull(schema.getProperties()).size() > 255;
        return hasOverLimitParameters || schema.hasAdditionalProperties() || schema.hasConstValue();
    }

    @Nullable
    public static String getInputFileName() {
        return inputFileName;
    }

    public static void setInputFileName(@Nullable String inputFileName) {
        SourceGenerator.inputFileName = inputFileName;
    }

    @Nullable
    public static Path getOutputPath() {
        return outputPath;
    }

    @Nullable
    public static String getOutputPackageName() {
        return outputPackageName;
    }

    public static VisitorContext.Language getLanguage() {
        return Objects.requireNonNull(language);
    }
}

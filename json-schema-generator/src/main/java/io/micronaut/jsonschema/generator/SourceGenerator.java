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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static String inputFileName = null;
    private static VisitorContext.Language language;
    private static Path outputPath;
    private static String outputPackageName;

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
        sourceGenerator = SourceGenerators.findByLanguage(language).orElse(null);
        if (sourceGenerator == null) {
            throw new RuntimeException("No source generator found for language " + language);
        }
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
    public File generate(SourceGeneratorConfig config) throws IOException {
        context.setConfiguration(config);
        outputPath = config.outputPath();
        outputPackageName = config.outputPackageName();
        if (config.inputFolder() != null) {
            generateFolder(config);
        } else {
            Schema jsonSchema = getJsonSchema(config);
            assert jsonSchema != null;
            inputFileName = getInputFileName() != null ? getInputFileName() : config.getInputName();
            if (config.outputFileName() != null && !config.outputFileName().isBlank()) {
                return generateSingleSchema(config, jsonSchema, false);
            }
            saveDefinitions(jsonSchema);
            return generateSingleSchema(config, jsonSchema, false);
        }
        return null;
    }

    /**
     * Generates source code from an already prepared schema.
     *
     * @param config The generator configuration
     * @param jsonSchema The schema to generate from
     * @return The top level schema's generated File, or null when no top level type is generated
     * @throws IOException If an I/O error occurs during source generation
     */
    public File generate(SourceGeneratorConfig config, Schema jsonSchema) throws IOException {
        context.setConfiguration(config);
        outputPath = config.outputPath();
        outputPackageName = config.outputPackageName();
        inputFileName = config.getInputName();
        // The record pipeline may pass a schema that was already prepared in memory.
        // Register definitions from that schema instead of loading/parsing it again.
        saveDefinitions(jsonSchema);
        return generateSingleSchema(config, jsonSchema, true);
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

    private File generateSingleSchema(SourceGeneratorConfig config, Schema jsonSchema, boolean generateDefinitionTypesForNamedOutput) throws IOException {
        // single java object is generated
        if (config.outputFileName() != null && !config.outputFileName().isBlank()) {
            var outputFileName = config.outputFileName();
            // remove extension from file name if there is
            if (config.outputFileName().contains(".")) {
                outputFileName = outputFileName.substring(0, outputFileName.indexOf('.'));
            }
            File topLevelObject = generateFromSchema(jsonSchema, config.outputPath(), config.outputPackageName(), outputFileName);
            if (generateDefinitionTypesForNamedOutput) {
                // Prepared schemas can still reference generated definition types even when
                // the top-level output name is supplied by the pipeline.
                generateDefinitionTypes(jsonSchema, config.outputPath(), config.outputPackageName());
            }
            return topLevelObject;
        } else {
            return generateDefinitions(jsonSchema, config.outputPath(), config.outputPackageName());
        }
    }

    private void saveDefinitions(Schema jsonSchema) {
        String schemaName = jsonSchema.hasTitle() ? jsonSchema.getTitle() : inputFileName.substring(0, inputFileName.indexOf('.'));
        String finalSchemaName = getClassName(schemaName);

        // save all definition and oneOf types
        if (jsonSchema.hasOneOf()) {
            jsonSchema.getOneOf().forEach(oneOf -> {
                if (oneOf.has$ref()) {
                    String ref = oneOf.get$ref();
                    if (ref.indexOf("#") == 0) {
                        ref = inputFileName + ref;
                    }
                    context.addOneOf(ref);
                } else {
                    context.addOneOf(oneOf);
                }
            });
        }
        if (jsonSchema.has$defs()) {
            jsonSchema.get$defs().forEach((s, schema) -> {
                if (s.equals("//")) {
                    if (!jsonSchema.hasDescription()) {
                        jsonSchema.setDescription(String.valueOf(schema));
                    } else {
                        jsonSchema.setDescription(jsonSchema.getDescription() + "<br>" + schema);
                    }
                } else if (schema.hasOneOf() && jsonSchema.hasDiscriminator()) {
                    // WARNING: assumes the same interface as top level schema
                    context.addDefinition(inputFileName + DEF_SCHEMA_REF_PREFIX + s, TypeDef.THIS, true);
                } else {
                    context.addDefinition(inputFileName + DEF_SCHEMA_REF_PREFIX + s, schema);
                }
            });
        }
        context.addDefinition(inputFileName + "#/" + finalSchemaName, jsonSchema);
    }

    private File generateDefinitions(Schema jsonSchema, Path outputPath, String packageName) throws IOException {
        // generate top level schema
        String schemaName = jsonSchema.hasTitle() ? jsonSchema.getTitle() : inputFileName.substring(0, inputFileName.indexOf('.'));
        schemaName = getClassName(schemaName);

        File topLevelObject = generateFromSchema(jsonSchema, outputPath, packageName, schemaName);

        generateDefinitionTypes(jsonSchema, outputPath, packageName);
        return topLevelObject;
    }

    private void generateDefinitionTypes(Schema jsonSchema, Path outputPath, String packageName) throws IOException {
        // generate classes in definitions and oneOfs
        if (jsonSchema.has$defs()) {
            jsonSchema.get$defs().entrySet()
                .stream()
                // Only emit definitions that were registered as real Java types while resolving references.
                .filter(definition -> !definition.getKey().equals("//") && context.isDefinitionClass(inputFileName + DEF_SCHEMA_REF_PREFIX + definition.getKey()))
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
    }

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
        for (Object anEnum : jsonSchema.getEnumValues()) {
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
                defaultValue = cases.get(ExpressionDef.constant(jsonSchema.getDefaultValue()));
            } else {
                defaultValue = ExpressionDef.nullValue();
            }
            // get enum value TypeDef
            Schema.Type valueType = jsonSchema.hasType() ? jsonSchema.getType().get(0) : Schema.Type.STRING;
            if (valueType.equals(Schema.Type.NULL)) {
                valueType = Schema.Type.STRING;
            }
            TypeDef valueTypeDef = TYPE_MAP_NULLABLE.get(valueType.toString().toLowerCase(Locale.ENGLISH));
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
        addJsonSchemaAnnotation(objectBuilder);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private ClassDef buildClass(Schema jsonSchema, String builderClassName) {
        ClassDef.ClassDefBuilder objectBuilder = ClassDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));
        addJsonSchemaAnnotation(objectBuilder);

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

            Map<String, Schema> properties = jsonSchema.getProperties();
            if (properties.containsKey(discriminatorProperty)) {
                objectBuilder.addField(
                    FieldDef.builder(discriminatorProperty)
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
            .addAnnotation(ClassTypeDef.of(SERDEABLE_ANN));
        addJsonSchemaAnnotation(objectBuilder);
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
        if (jsonSchema.hasDescription()) {
            builder.addJavadoc(getJavadoc(jsonSchema.getDescription()));
        }

        if (jsonSchema.hasProperties()) {
            if (context.isStrictUnsupportedKeywords()) {
                // The record profile fails fast for generated member collisions instead of
                // silently overwriting fields after Java-name sanitization.
                validateMemberNameCollisions(jsonSchema);
            }
            List<String> requiredProperties = (jsonSchema.getRequired() != null) ? jsonSchema.getRequired() : new ArrayList<>();
            jsonSchema.getProperties().entrySet().forEach(entry -> addField(
                    builder,
                    entry.getKey(),
                    entry.getValue(),
                    requiredProperties.contains(entry.getKey())
                ));

            if (shouldGenerateAdditionalProperties(jsonSchema)) {
                addAdditionalField(jsonSchema, builder);
            }
        } else if (context.isStrictUnsupportedKeywords() && shouldGenerateAdditionalProperties(jsonSchema)) {
            // Existing generation only adds the open-object member after declared properties.
            // The record profile also models explicitly open objects with no declared properties.
            addAdditionalField(jsonSchema, builder);
        }
    }

    private void addAdditionalField(Schema jsonSchema, ObjectDefBuilder builder) {
        TypeDef mapType;
        if (jsonSchema.getAdditionalProperties().equals(Schema.TRUE)) {
            mapType = TypeDef.OBJECT;
        } else {
            // Map value types cannot be primitive Java types.
            mapType = boxPrimitive(getTypeDefFromJson(jsonSchema.getAdditionalProperties(), context));
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
        if (context.isStrictUnsupportedKeywords() && !isRequired && propertyType instanceof TypeDef.Primitive primitive) {
            // Optional record-profile properties represent absence, so scalar types must be boxed.
            propertyType = primitive.wrapperType();
        }
        // add annotations
        var annotations = AnnotationsAggregator.getAnnotations(schema, propertyType, isRequired);
        if (!annotations.isEmpty()) {
            propertyType = propertyType.annotated(annotations);
        }
        propertyDef.ofType(propertyType);

        // add javadoc
        if (schema.hasDescription()) {
            propertyDef.addJavadoc(getJavadoc(schema.getDescription()));
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
        if (context.isStrictUnsupportedKeywords() && SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)) {
            // At property level an ambiguous allOf is recoverable: keep generating the owner type
            // and make this member broad rather than emitting an invalid partial shape.
            context.warn("UNSUPPORTED_KEYWORD", "allOf cannot be flattened deterministically at property level; using java.lang.Object");
            return TypeDef.OBJECT;
        }
        // add type info and type validation annotations
        TypeDef propertyType = getTypeDefFromJson(schema, context);
        if (context.isStrictUnsupportedKeywords() && hasUnsupportedPropertyShape(schema)) {
            // The record profile reports unsupported unions/refs through warnings and uses Object
            // for the affected property; root-level failures are handled by the pipeline.
            return TypeDef.OBJECT;
        }
        if (schema.isEnum()) {
            propertyType = getEnumType(objectBuilder, name, schema);
        } else if (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getListTypeDef(objectBuilder, name, schema);
        } else if (propertyType.equals(TypeDef.OBJECT) && schema.hasProperties()) {
            propertyType = buildInnerType(objectBuilder, name, schema);
        } else if (propertyType.equals(TypeDef.OBJECT) && schema.hasAdditionalProperties()) {
            if (schema.getAdditionalProperties().equals(Schema.TRUE)) {
                return TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING, TypeDef.OBJECT);
            } else {
                return TypeDef.parameterized(ClassTypeDef.of(Map.class), TypeDef.STRING,
                    // Map value types cannot be primitive Java types.
                    boxPrimitive(getPropertyType(objectBuilder, schema.getAdditionalProperties(), name + "Item")));
            }
        }
        return propertyType;
    }

    private boolean hasUnsupportedPropertyShape(Schema schema) {
        if (schema.hasOneOf()) {
            // oneOf can express polymorphic validation, but the record profile cannot choose a
            // single property type here without losing the "exactly one branch" semantics.
            return true;
        }
        if (schema.hasType() && schema.getType().stream()
            .filter(type -> !Schema.Type.NULL.equals(type))
            .distinct()
            .count() > 1) {
            // A union like ["string", "null"] is handled as nullable string elsewhere. Multiple
            // non-null types, for example ["string", "integer"], have no precise Java member type.
            return true;
        }
        if (schema.has$ref()) {
            String ref = schema.get$ref();
            if (Schema.THIS_SCHEMA_REF.equals(ref)) {
                // Recursive self-reference maps to the generated enclosing type.
                return false;
            }
            if (ref.startsWith("#")) {
                // Supported same-document refs are registered in GeneratorContext before property
                // generation. If a local ref is still unknown here, keep the owner type valid by
                // falling back to Object for this property.
                return !context.hasDefinition(inputFileName + ref);
            }
            // The record pipeline does not fetch remote schemas during generation.
            return true;
        }
        return false;
    }

    private void addDiscriminatorAnnotations(Schema jsonSchema, ObjectDefBuilder objectBuilder) {
        if (!jsonSchema.hasDiscriminator()) {
            return;
        }
        var discriminator = jsonSchema.getDiscriminator();
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
        EnumDef enumDef = buildEnum(schema, getClassName(propertyName));
        objectBuilder.addInnerType(enumDef);
        return enumDef.asTypeDef();
    }

    private TypeDef getListTypeDef(ObjectDefBuilder objectBuilder, String propertyName, Schema schema) {
        Schema items = schema.getItems() != null ? schema.getItems() : schema.getContains();
        if (items == null) {
            // In the record profile, arrays with omitted items are still arrays of unconstrained values.
            // Keep existing default behavior outside that profile.
            return context.isStrictUnsupportedKeywords()
                ? TypeDef.parameterized(ClassTypeDef.of(List.class), TypeDef.OBJECT)
                : TypeDef.OBJECT;
        }

        if (context.isStrictUnsupportedKeywords() && items.hasType() && items.getType().contains(io.micronaut.jsonschema.model.Schema.Type.NULL)) {
            // Convert item type ["T", "null"] into the generator's existing nullable annotation path.
            items.setNullable(true);
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
        // Sanitization here matches top-level and definition class naming for record-profile nested types.
        String nestedTypeName = getClassName(propertyName);
        if (shouldBeAClass(schema)) {
            builder = buildClass(schema, nestedTypeName);
        } else {
            builder = buildRecord(schema, nestedTypeName);
        }
        objectBuilder.addInnerType(builder);
        return ClassTypeDef.of(builder.getName());
    }

    private boolean shouldBeAClass(Schema schema) {
        if (context.getConfiguration().recordAdoptionStrategy() == RecordAdoptionStrategy.ALWAYS_CLASS) {
            return true;
        }
        boolean hasOverLimitParameters = schema.hasProperties() && schema.getProperties().size() > 255;
        return hasOverLimitParameters || schema.hasAdditionalProperties() || schema.hasConstValue();
    }

    public static String getInputFileName() {
        return inputFileName;
    }

    public static void setInputFileName(String inputFileName) {
        SourceGenerator.inputFileName = inputFileName;
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

    /**
     * Get recorded warnings for the latest generation run.
     * @return The warnings
     */
    public List<GeneratorContext.Warning> getWarnings() {
        return context.getWarnings();
    }

    private boolean shouldAddGeneratedJsonSchemaAnnotation() {
        return context.isAddGeneratedJsonSchemaAnnotation();
    }

    private void addJsonSchemaAnnotation(ObjectDefBuilder builder) {
        if (shouldAddGeneratedJsonSchemaAnnotation()) {
            // Enabled by the record-generation profile so emitted models can be discovered as schema-backed types.
            builder.addAnnotation(ClassTypeDef.of(io.micronaut.jsonschema.JsonSchema.class));
        }
    }

    private boolean shouldGenerateAdditionalProperties(Schema schema) {
        return schema.hasAdditionalProperties() && !Schema.FALSE.equals(schema.getAdditionalProperties());
    }

    private TypeDef boxPrimitive(TypeDef type) {
        if (type instanceof TypeDef.Primitive primitive) {
            return primitive.wrapperType();
        }
        return type;
    }

    private void validateMemberNameCollisions(Schema schema) {
        Map<String, List<String>> jsonNamesByJavaName = new LinkedHashMap<>();
        if (schema.hasProperties()) {
            schema.getProperties().keySet().forEach(jsonName ->
                jsonNamesByJavaName.computeIfAbsent(getPropertyName(jsonName), ignored -> new LinkedList<>()).add(jsonName));
        }
        if (shouldGenerateAdditionalProperties(schema)) {
            // The open-object member is generated with a fixed name, so user properties must not
            // sanitize to the same Java member name.
            jsonNamesByJavaName.computeIfAbsent("unknownFields", ignored -> new LinkedList<>()).add("<additionalProperties>");
        }
        jsonNamesByJavaName.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .findFirst()
            .ifPresent(entry -> {
                throw new IllegalArgumentException("NAME_COLLISION: JSON properties " + entry.getValue()
                    + " resolve to Java member name '" + entry.getKey() + "'");
            });
    }
}

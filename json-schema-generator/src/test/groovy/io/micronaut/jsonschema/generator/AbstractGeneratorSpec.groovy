package io.micronaut.jsonschema.generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.jsonschema.generator.loaders.FileProcessor
import io.micronaut.jsonschema.generator.utils.GeneratorContext
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.function.Consumer

class AbstractGeneratorSpec extends Specification {

    TypeDeclaration generateType(String className, String jsonSchema, Consumer<SourceGeneratorConfigBuilder> consumer) {
        SourceGenerator generator = new SourceGenerator("java")

        Path outputPath = Files.createTempDirectory("json-schema-generator-output")
        try {
            String packageName = "com.example.project"; // Example package name
            var builder = new SourceGeneratorConfigBuilder()
                    .withInputStream(new ByteArrayInputStream(jsonSchema.getBytes()))
                    .withOutputFolder(outputPath)
                    .withOutputFileName(className)
                    .withOutputPackageName(packageName)
            consumer.accept(builder)
            return parseGeneratedType(generator.generate(builder.build()))
        } finally {
            deleteRecursively(outputPath)
        }
    }

    TypeDeclaration generatePreparedCompositionType(String className,
                                                    String jsonSchema,
                                                    Consumer<SourceGeneratorConfigBuilder> consumer) {
        SourceGenerator generator = new SourceGenerator("java")

        Path outputPath = Files.createTempDirectory("json-schema-generator-output")
        try {
            String packageName = "com.example.project"; // Example package name
            var builder = new SourceGeneratorConfigBuilder()
                .withInputStream(new ByteArrayInputStream(jsonSchema.getBytes()))
                .withOutputFolder(outputPath)
                .withOutputFileName(className)
                .withOutputPackageName(packageName)
            consumer.accept(builder)
            SourceGeneratorConfig config = builder.build()
            var schema = FileProcessor.getJsonSchema(config)
            // This mirrors the record-generator pipeline, where schema composition and local
            // definition refs are resolved before handing the schema to SourceGenerator.
            SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)
            return parseGeneratedType(generator.generate(config, schema))
        } finally {
            deleteRecursively(outputPath)
        }
    }

    TypeDeclaration generateRecordProfileType(String className, String jsonSchema) {
        GeneratorContext context = new GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(VisitorContext.Language.JAVA, context)

        Path outputPath = Files.createTempDirectory("json-schema-generator-output")
        try {
            String packageName = "com.example.project"; // Example package name
            var config = new SourceGeneratorConfigBuilder()
                .withInputStream(new ByteArrayInputStream(jsonSchema.getBytes()))
                .withOutputFolder(outputPath)
                .withOutputFileName(className)
                .withOutputPackageName(packageName)
                .build()
            return parseGeneratedType(generator.generate(config))
        } finally {
            deleteRecursively(outputPath)
        }
    }

    private static TypeDeclaration parseGeneratedType(File generated) {
        try {
            ParserConfiguration configuration = new ParserConfiguration()
            configuration.languageLevel = ParserConfiguration.LanguageLevel.JAVA_17
            ParseResult<CompilationUnit> parsed = new JavaParser(configuration).parse(generated.text)
            return parsed.getResult().get().getType(0)
        } catch (Exception e) {
            throw new Exception("Failed to parse file and get record. The contents are: '\n" + generated.text + "\n'", e)
        }
    }

    private static void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    String generateRecordProfileTypeAndGetContent(String className, String jsonSchema) {
        return generateRecordProfileType(className, jsonSchema).getTokenRange().get().toString()
    }

    TypeDeclaration generatePreparedCompositionType(String className, String jsonSchema) {
        return generatePreparedCompositionType(className, jsonSchema, b -> {})
    }

    String generatePreparedCompositionTypeAndGetContent(String className,
                                                        String jsonSchema,
                                                        Consumer<SourceGeneratorConfigBuilder> configConsumer) {
        return generatePreparedCompositionType(className, jsonSchema, configConsumer).getTokenRange().get().toString()
    }

    String generatePreparedCompositionTypeAndGetContent(String className, String jsonSchema) {
        return generatePreparedCompositionType(className, jsonSchema).getTokenRange().get().toString()
    }

    TypeDeclaration generateType(String className, String jsonSchema) {
        return generateType(className, jsonSchema, b -> {})
    }

    String generateTypeAndGetContent(String className, String jsonSchema, Consumer<SourceGeneratorConfigBuilder> configConsumer) {
        return generateType(className, jsonSchema, configConsumer).getTokenRange().get().toString()
    }

    String generateTypeAndGetContent(String className, String jsonSchema) {
        return generateType(className, jsonSchema).getTokenRange().get().toString()
    }

    String generatePropertyAndGetContent(String propertyName, String propertySchema) {
        String schema = """
        {
          "\$schema":"https://json-schema.org/draft/2020-12/schema",
          "\$id":"https://example.com/schemas/test.schema.json",
          "title":"Test",
          "type":["object"],
          "properties":{
            "$propertyName": $propertySchema
          }
        }
        """

        return ((RecordDeclaration) generateType("TestRecord", schema))
                .parameters[0].getTokenRange().get().toString()
    }

    String generateRecordProfilePropertyAndGetContent(String propertyName, String propertySchema) {
        String schema = """
        {
          "\$schema":"https://json-schema.org/draft/2020-12/schema",
          "\$id":"https://example.com/schemas/test.schema.json",
          "title":"Test",
          "type":["object"],
          "properties":{
            "$propertyName": $propertySchema
          }
        }
        """

        return ((RecordDeclaration) generateRecordProfileType("TestRecord", schema))
                .parameters[0].getTokenRange().get().toString()
    }

}

package io.micronaut.jsonschema.generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder
import spock.lang.Specification

import java.nio.file.Path
import java.util.function.Consumer

class AbstractGeneratorSpec extends Specification {

    TypeDeclaration generateType(String className, String jsonSchema, Consumer<SourceGeneratorConfigBuilder> consumer) {
        SourceGenerator generator = new SourceGenerator("java")
        SourceGenerator.setInputFileName("test.schema.json")

        Path outputPath = new File("output").toPath() // Define the base output path
        String packageName = "com.example.project"; // Example package name

        var builder = new SourceGeneratorConfigBuilder()
                .withInputStream(new ByteArrayInputStream(jsonSchema.getBytes()))
                .withOutputFolder(outputPath)
                .withOutputFileName(className)
                .withOutputPackageName(packageName)
        consumer.accept(builder)
        File generated = generator.generate(builder.build());

        try {
            ParserConfiguration configuration = new ParserConfiguration()
            configuration.languageLevel = ParserConfiguration.LanguageLevel.JAVA_17
            ParseResult<CompilationUnit> parsed = new JavaParser(configuration).parse(generated.text)
            return parsed.getResult().get().getType(0)
        } catch (Exception e) {
            throw new Exception("Failed to parse file and get record. The contents are: '\n" + generated.text + "\n'", e)
        }
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

}

package io.micronaut.jsonschema.generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import spock.lang.Specification

import java.nio.file.Path

class AbstractGeneratorSpec extends Specification {

    TypeDeclaration generateType(String className, String jsonSchema) {
        SourceGenerator generator = new SourceGenerator(VisitorContext.Language.JAVA)

        Path outputPath = new File("output").toPath() // Define the base output path
        String packageName = "com.example.project"; // Example package name
        String fileName = className;

        File generated = generator.generate(new SourceGeneratorConfig(
                new ByteArrayInputStream(jsonSchema.getBytes()), null, null,
                null, outputPath, packageName, fileName))

        try {
            ParserConfiguration configuration = new ParserConfiguration()
            configuration.languageLevel = ParserConfiguration.LanguageLevel.JAVA_17
            ParseResult<CompilationUnit> parsed = new JavaParser(configuration).parse(generated.text)
            return parsed.getResult().get().getType(0)
        } catch (Exception e) {
            throw new Exception("Failed to parse file and get record. The contents are: '\n" + generated.text + "\n'", e)
        }
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

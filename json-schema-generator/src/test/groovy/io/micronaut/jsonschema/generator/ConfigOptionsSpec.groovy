package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.loaders.UrlLoader
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder

import java.nio.file.Path

import static io.micronaut.jsonschema.generator.loaders.UrlLoader.isValidUrl

class ConfigOptionsSpec extends AbstractGeneratorSpec {

    void testJavadocGenerationWithoutReplacement() {
        when:
        var type = generateType("Elephant", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/elephant.schema.json",
          "title": "Elephant",
          "description":"A elephant <a href=\\"https://elephant.com\\">elephant URL</a>.\\n Another line",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''', b -> b.withJavadoc(new SourceGeneratorConfig.JavadocConfig(false)))

        then:
        type.getJavadoc().get().toText() == """A elephant <a href="https://elephant.com">elephant URL</a>.\n Another line\n"""
    }

    void testGenerateAlwaysClass() {
        when:
        var content = generateTypeAndGetContent("Porcupine", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/elephant.schema.json",
          "title": "Porcupine",
          "description":"A porcupine",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''', b -> b.withRecordAdoptionStrategy(SourceGeneratorConfig.RecordAdoptionStrategy.ALWAYS_CLASS))

        then:
        content == """
        @Serdeable
        public class Porcupine {
          private String name;

          public String getName() {
            return this.name;
          }

          public void setName(String name) {
            this.name = name;
          }
        }
        """.stripIndent().trim()
    }

    void testGeneratedAnnotationIsNotAddedByDefault() {
        when:
        var type = generateType("Owl", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title": "Owl",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''')

        then:
        type.annotations*.nameAsString == ["Serdeable"]
    }

    void testGeneratedAnnotation() {
        when:
        var type = generateType("Heron", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title": "Heron",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            },
            "nest": {
              "type": "object",
              "properties": {
                "height": {
                  "type": "integer"
                }
              }
            },
            "plumage": {
              "type": "string",
              "enum": ["grey", "white"]
            }
          }
        }
        ''', b -> b.withGeneratedAnnotation(true))

        then:
        type.findCompilationUnit().get().imports*.nameAsString.contains("io.micronaut.jsonschema.GeneratedFromJsonSchema")
        type.annotations*.nameAsString == ["Serdeable", "GeneratedFromJsonSchema"]
        type.members.findAll { it.isTypeDeclaration() }.collect { it.asTypeDeclaration() }.every {
            it.annotations*.nameAsString.containsAll(["Serdeable", "GeneratedFromJsonSchema"])
        }
        type.members.count { it.isTypeDeclaration() } == 2
    }

    void testGeneratedAnnotationOnInterface() {
        when:
        var type = generateType("Bird", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title": "Bird",
          "oneOf": [
            {"title": "Crow", "type": "object", "properties": {"name": {"type": "string"}}},
            {"title": "Swan", "type": "object", "properties": {"color": {"type": "string"}}}
          ]
        }
        ''', b -> b.withGeneratedAnnotation(true))

        then:
        type.isClassOrInterfaceDeclaration()
        type.asClassOrInterfaceDeclaration().isInterface()
        type.annotations*.nameAsString == ["Serdeable", "GeneratedFromJsonSchema"]
    }

    void testGeneratedAnnotationOnClass() {
        when:
        var content = generateTypeAndGetContent("Porcupine", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title": "Porcupine",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''', b -> b
            .withRecordAdoptionStrategy(SourceGeneratorConfig.RecordAdoptionStrategy.ALWAYS_CLASS)
            .withGeneratedAnnotation(true))

        then:
        content.startsWith("""
        @Serdeable
        @GeneratedFromJsonSchema
        public class Porcupine {
        """.stripIndent().trim())
    }

    void testOutputPathIsRequired() {
        when:
        new SourceGeneratorConfigBuilder().build()

        then:
        def e = thrown(NullPointerException)
        e.message == "Source generator outputPath is required"
    }

}

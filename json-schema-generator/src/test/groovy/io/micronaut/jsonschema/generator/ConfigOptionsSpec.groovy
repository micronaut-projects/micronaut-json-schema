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

          String getName() {
            return this.name;
          }

          void setName(String name) {
            this.name = name;
          }
        }
        """.stripIndent().trim()
    }

}

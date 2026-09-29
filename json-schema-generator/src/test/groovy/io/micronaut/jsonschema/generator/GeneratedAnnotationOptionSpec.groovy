package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneration
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneratorConfig
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsManifest
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GeneratedAnnotationOptionSpec extends Specification {

    @TempDir
    Path tempDir

    @Shared
    @TempDir
    File sharedDir

    void "source generator config without the generated annotation flag defaults it to false"() {
        when:
        var config = new SourceGeneratorConfig(null, null, null, null, tempDir, "example", "",
            new SourceGeneratorConfig.JavadocConfig(), SourceGeneratorConfig.RecordAdoptionStrategy.PREFER_RECORD)

        then:
        !config.generatedAnnotation()
    }

    void "source generator config keeps the generated annotation flag when converted to a builder"() {
        given:
        var config = new SourceGeneratorConfig(null, null, null, null, tempDir, "example", "",
            new SourceGeneratorConfig.JavadocConfig(), SourceGeneratorConfig.RecordAdoptionStrategy.PREFER_RECORD, true)

        expect:
        config.toBuilder().build().generatedAnnotation()
    }

    void "records generation defaults the generated annotation flag to false"() {
        expect:
        !generation.generatedAnnotation()
        !generation.toGeneratorConfig().generatedAnnotation()

        where:
        generation << [
            new JsonSchemaRecordsGeneration(null, null, null, "example", 21, sharedDir, sharedDir, [], false, true),
            new JsonSchemaRecordsGeneration(null, null, null, "example", 21, sharedDir, sharedDir, [], false, true, "JAVA"),
            new JsonSchemaRecordsGeneration(null, null, null, "example", 21, sharedDir, sharedDir, [], false, true, "JAVA", null)
        ]
    }

    void "records generation passes the generated annotation flag to the generator configuration"() {
        when:
        var generation = new JsonSchemaRecordsGeneration(null, null, null, "example", 21, tempDir.toFile(), tempDir.toFile(),
            [], false, true, "JAVA", true)

        then:
        generation.generatedAnnotation()
        generation.toGeneratorConfig().generatedAnnotation()
    }

    void "records generator configuration and manifest parameters without the flag default it to false"() {
        expect:
        !new JsonSchemaRecordsGeneratorConfig(null, null, null, "example", 21, tempDir, tempDir, [], false, true, "JAVA")
            .generatedAnnotation()
        !new JsonSchemaRecordsManifest.Parameters("example", "JAVA", 21, "cache", "out", [], false, true)
            .generatedAnnotation()
    }

    void "generator main passes the generated annotation flag to the generator"() {
        given:
        Path schema = tempDir.resolve("heron.schema.json")
        Files.writeString(schema, '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title": "Heron",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''')
        Path output = tempDir.resolve(generatedAnnotation ? "annotated" : "plain")

        when:
        GeneratorMain.main(["", schema.toUri().toString(), "", "JAVA", output.toString(), "com.example", "", "[]",
            String.valueOf(generatedAnnotation)] as String[])

        then:
        Files.readString(output.resolve("com/example/Heron.java")).contains("@GeneratedFromJsonSchema") == generatedAnnotation

        where:
        generatedAnnotation << [true, false]
    }

    void "generator main requires all arguments"() {
        when:
        GeneratorMain.main(["", "", "", "JAVA", tempDir.toString(), "com.example", "", "[]"] as String[])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Invalid number of arguments."
    }
}

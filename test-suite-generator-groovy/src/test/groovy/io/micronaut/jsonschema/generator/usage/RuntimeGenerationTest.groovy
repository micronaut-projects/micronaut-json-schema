package io.micronaut.jsonschema.generator.usage

import io.micronaut.jsonschema.generator.SourceGenerator
import io.micronaut.jsonschema.generator.loaders.UrlLoader
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class RuntimeGenerationTest extends Specification {

    @TempDir
    Path outputPath

    void "generate from schema"() {
        given:
        File schemaFile = new File("../test-suite-generator-java/src/test/resources/animal.schema.json")

        when:
        // tag::generate[]
        // create a generator with your chosen language
        def javaGenerator = new SourceGenerator("JAVA")

        // Optional: can configure accepted URL references
        UrlLoader.allowedUrlPatterns = ['^https://.*/.*.schema.json$'] // <1>
        UrlLoader.addAllowedUrlPattern('^http://localhost:.*')

        // Optional: set up input file name
        String schemaFileName = "example.schema.json"
        SourceGenerator.inputFileName = schemaFileName // <2>

        String packageName = "com.example.temp" // Example package name
        SourceGeneratorConfig config = new SourceGeneratorConfigBuilder()
                .withOutputFolder(outputPath) // Define the base output path
                .withOutputPackageName(packageName)
                .withJsonFile(schemaFile)
                .build()

        File generated = javaGenerator.generate(config) // <3>
        // end::generate[]

        then:
        generated != null
        generated.name == "Animal.java"
        Files.exists(outputPath.resolve("com/example/temp/Animal.java"))
        Files.exists(outputPath.resolve("com/example/temp/Cat.java"))
    }
}

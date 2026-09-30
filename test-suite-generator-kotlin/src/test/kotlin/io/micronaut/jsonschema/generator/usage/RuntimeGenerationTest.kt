package io.micronaut.jsonschema.generator.usage

import io.micronaut.jsonschema.generator.SourceGenerator
import io.micronaut.jsonschema.generator.loaders.UrlLoader
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class RuntimeGenerationTest {

    @Test
    fun generateFromSchema(@TempDir outputPath: Path) {
        val schemaFile = File("../test-suite-generator-java/src/test/resources/animal.schema.json")
        // tag::generate[]
        // create a generator with your chosen language
        val javaGenerator = SourceGenerator("JAVA")

        // Optional: can configure accepted URL references
        UrlLoader.setAllowedUrlPatterns(listOf("^https://.*/.*.schema.json$")) // <1>
        UrlLoader.addAllowedUrlPattern("^http://localhost:.*")

        // Optional: set up input file name
        val schemaFileName = "example.schema.json"
        SourceGenerator.setInputFileName(schemaFileName) // <2>

        val packageName = "com.example.temp" // Example package name
        val config = SourceGeneratorConfigBuilder()
            .withOutputFolder(outputPath) // Define the base output path
            .withOutputPackageName(packageName)
            .withJsonFile(schemaFile)
            .build()

        val generated = javaGenerator.generate(config) // <3>
        // end::generate[]

        assertNotNull(generated)
        assertEquals("Animal.java", generated!!.name)
        assertTrue(Files.exists(outputPath.resolve("com/example/temp/Animal.java")))
        assertTrue(Files.exists(outputPath.resolve("com/example/temp/Cat.java")))
    }
}

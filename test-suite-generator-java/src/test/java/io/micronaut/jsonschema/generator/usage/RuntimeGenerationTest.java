package io.micronaut.jsonschema.generator.usage;

import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.loaders.UrlLoader;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeGenerationTest {

    @Test
    void generateFromSchema(@TempDir Path outputPath) throws IOException {
        File schemaFile = new File("src/test/resources/animal.schema.json");
        // tag::generate[]
        // create a generator with your chosen language
        var javaGenerator = new SourceGenerator("JAVA");

        // Optional: can configure accepted URL references
        UrlLoader.setAllowedUrlPatterns(List.of("^https://.*/.*.schema.json$")); // <1>
        UrlLoader.addAllowedUrlPattern("^http://localhost:.*");

        // Optional: set up input file name
        String schemaFileName = "example.schema.json";
        SourceGenerator.setInputFileName(schemaFileName); // <2>

        String packageName = "com.example.temp"; // Example package name
        SourceGeneratorConfig config = new SourceGeneratorConfigBuilder()
            .withOutputFolder(outputPath) // Define the base output path
            .withOutputPackageName(packageName)
            .withJsonFile(schemaFile)
            .build();

        File generated = javaGenerator.generate(config); // <3>
        // end::generate[]

        assertNotNull(generated);
        assertEquals("Animal.java", generated.getName());
        assertTrue(Files.exists(outputPath.resolve("com/example/temp/Animal.java")));
        assertTrue(Files.exists(outputPath.resolve("com/example/temp/Cat.java")));
    }
}

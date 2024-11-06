package io.micronaut.jsonschema.generator;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@MicronautTest(startApplication = false)
class ObjectGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @Test
    void objectGenerator() throws IOException {
        var generator = new CodeGenerator();
        String schemaFileName = "llama.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.project"; // Example package name
        String fileName = "Llama.java";
        Assertions.assertNotNull(generator.generate(inputStream.get(), VisitorContext.Language.JAVA, outputPath, packageName, fileName));
    }

    @Test
    void folderGenerator() throws IOException {
        var generator = new CodeGenerator();
        String schemaFileName = "llama.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.project"; // Example package name
        int generatedFiles = generator.generate(inputStream.get(), VisitorContext.Language.JAVA, outputPath, packageName);
        Assertions.assertEquals(1, generatedFiles);
    }

    @Test
    void folderGenerator2() throws IOException {
        var generator = new CodeGenerator();
        String schemaFileName = "animal.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.animals"; // Example package name
        int generatedFiles = generator.generate(inputStream.get(), VisitorContext.Language.JAVA, outputPath, packageName);
        Assertions.assertEquals(4, generatedFiles);
    }
}

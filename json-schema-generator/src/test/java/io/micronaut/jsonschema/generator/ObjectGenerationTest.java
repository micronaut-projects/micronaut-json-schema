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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@MicronautTest(startApplication = false)
class ObjectGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @Test
    void objectGenerator() throws IOException {
        var generator = new CodeGenerator(VisitorContext.Language.JAVA);
        String schemaFileName = "llama.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.project"; // Example package name
        String fileName = "Llama2.java";
        Assertions.assertNotNull(generator.generate(inputStream.get(), outputPath, packageName, fileName));
    }

    @Test
    void folderGenerator() throws IOException {
        var generator = new CodeGenerator(VisitorContext.Language.JAVA);
        String schemaFileName = "llama.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.project"; // Example package name
        int generatedFiles = generator.generate(inputStream.get(), outputPath, packageName);
        Assertions.assertEquals(1, generatedFiles);
    }

    @Test
    void folderGenerator2() throws IOException {
        var generator = new CodeGenerator(VisitorContext.Language.JAVA);
        String schemaFileName = "animal.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.animals"; // Example package name
        int generatedFiles = generator.generate(inputStream.get(), outputPath, packageName);
        Assertions.assertEquals(5, generatedFiles);

        // Assert that the expected files exist
        String[] expectedFileNames = {
            "com/example/animals/Animal.java",
            "com/example/animals/Cat.java",
            "com/example/animals/Dog.java",
            "com/example/animals/Fish.java",
            "com/example/animals/Human.java"
        };
        for (String expectedFileName : expectedFileNames) {
            Path expectedFilePath = outputPath.resolve(expectedFileName);
            Assertions.assertTrue(Files.exists(expectedFilePath), "Expected file not found: " + expectedFilePath);
        }
    }

    @Test
    void folderGenerator3() throws IOException {
        var generator = new CodeGenerator(VisitorContext.Language.JAVA);
        String schemaFileName = "fhir.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        Path outputPath = Paths.get("output"); // Define the base output path
        String packageName = "com.example.fhir"; // Example package name
        int generatedFiles = generator.generate(inputStream.get(), outputPath, packageName);
        Assertions.assertEquals(864, generatedFiles);

        // Assert that the expected files exist
        String[] expectedFileNames = {
            "com/example/fhir/ResourceList.java",
            "com/example/fhir/Account.java",
            "com/example/fhir/BackboneType.java",
            "com/example/fhir/Xhtml.java"
        };
        for (String expectedFileName : expectedFileNames) {
            Path expectedFilePath = outputPath.resolve(expectedFileName);
            Assertions.assertTrue(Files.exists(expectedFilePath), "Expected file not found: " + expectedFilePath);
        }

        // Assert that primitive definitions are not generated
        String[] unexpectedFileNames = {
            "com/example/fhir/DateTime.java",
            "com/example/fhir/Integer.java",
            "com/example/fhir/Boolean.java",
            "com/example/fhir/Canonical.java"
        };
        for (String unexpectedFileName : unexpectedFileNames) {
            Path expectedFilePath = outputPath.resolve(unexpectedFileName);
            Assertions.assertFalse(Files.exists(expectedFilePath), "Unexpected file found: " + expectedFilePath);
        }
    }
}

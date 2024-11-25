package io.micronaut.jsonschema.generator.animals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GenerationTest {
    @Test
    void githubGenerator() throws IOException {
        Path outputPath = Paths.get("build/generated/jsonSchema/java/main");
        String packageName = "io.micronaut.jsonschema.generator.github".replace(".", "/");
        Path expectedFolderPath = outputPath.resolve(packageName);

        Assertions.assertTrue(Files.exists(outputPath), "Output folder path does not exist.");
        Assertions.assertTrue(Files.exists(expectedFolderPath), "Expected folder path does not exist.");
        long generatedFiles = 0;
        try {
            // Count the number of files (not directories) in the folder
            generatedFiles = Files.list(expectedFolderPath)
                .filter(Files::isRegularFile)
                .count();
        } catch (IOException e) {
            Assertions.fail("Failed to list files in the folder: " + e.getMessage());
        }
        Assertions.assertEquals(12, generatedFiles);
    }

    @Test
    void fhirGenerator() throws IOException {
        Path outputPath = Paths.get("build/generated/jsonSchema/java/main");
        String packageName = "io.micronaut.jsonschema.generator.fhir".replace(".", "/");
        Path expectedFolderPath = outputPath.resolve(packageName);

        Assertions.assertTrue(Files.exists(expectedFolderPath), "Expected folder path does not exist.");
        long generatedFiles = 0;
        try {
            // Count the number of files (not directories) in the folder
            generatedFiles = Files.list(expectedFolderPath)
                .filter(Files::isRegularFile)
                .count();
        } catch (IOException e) {
            Assertions.fail("Failed to list files in the folder: " + e.getMessage());
        }
        Assertions.assertEquals(864, generatedFiles);

        // Assert that the expected files exist
        String[] expectedFileNames = {
            "Fhir.java",
            "Account.java",
            "BackboneType.java",
            "Xhtml.java"
        };
        for (String expectedFileName : expectedFileNames) {
            Path expectedFilePath = expectedFolderPath.resolve(expectedFileName);
            Assertions.assertTrue(Files.exists(expectedFilePath), "Expected file not found: " + expectedFilePath);
        }

        // Assert that primitive definitions are not generated
        String[] unexpectedFileNames = {
            "DateTime.java",
            "Integer.java",
            "Boolean.java",
            "Canonical.java"
        };
        for (String unexpectedFileName : unexpectedFileNames) {
            Path expectedFilePath = expectedFolderPath.resolve(unexpectedFileName);
            Assertions.assertFalse(Files.exists(expectedFilePath), "Unexpected file found: " + expectedFilePath);
        }
    }
}

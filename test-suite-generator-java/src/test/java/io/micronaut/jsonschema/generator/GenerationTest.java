package io.micronaut.jsonschema.generator;

import io.micronaut.jsonschema.generator.ref.Cheatsheet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GenerationTest {
    @Test
    void githubGenerator() {
        Path outputPath = Paths.get("build/generated/jsonSchema/src/main/java");
        String packageName = "io.micronaut.jsonschema.generator.github".replace('.', File.separatorChar);
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
        Assertions.assertEquals(14, generatedFiles);
    }

    @Test
    void refGenerator() {
        Path outputPath = Paths.get("build/generated/jsonSchema/src/main/java");
        String packageName = "io.micronaut.jsonschema.generator.ref".replace('.', File.separatorChar);
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
        Assertions.assertEquals(32, generatedFiles);

        // Assert that the expected files exist
        String[] expectedFileNames = {
            "Cheatsheet.java",
            "Cv.java",
            "Head.java",
            "TargetJob.java",
            "CommonTypesForAllSchemas.java"
        };
        for (String expectedFileName : expectedFileNames) {
            Path expectedFilePath = expectedFolderPath.resolve(expectedFileName);
            Assertions.assertTrue(Files.exists(expectedFilePath), "Expected file not found: " + expectedFilePath);
        }
        var cheatpath = new Cheatsheet.Cheatpaths("name", "path", Set.of("tag"), true);
        assertNotNull(cheatpath);
    }

    @Test
    void wordPressGenerator() {
        Path outputPath = Paths.get("build/generated/jsonSchema/src/main/java");
        String packageName = "io.micronaut.jsonschema.generator.wordpress".replace('.', File.separatorChar);
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
        Assertions.assertEquals(1, generatedFiles);
    }
}

package io.micronaut.jsonschema.test;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.generator.RecordGenerator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class ObjectGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @Test
    void objectGenerator() throws IOException {
        File jsonFile = new File("llama.schema.json");
        var recordCreator = new RecordGenerator(resourceLoader);
        assertTrue(recordCreator.generate(jsonFile, Optional.empty()));
    }
}

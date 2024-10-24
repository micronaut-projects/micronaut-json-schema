package io.micronaut.jsonschema.test;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.generator.RecordGenerator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class ObjectGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @Test
    void objectGenerator() throws IOException {
        var generator = new RecordGenerator();
        String schemaFileName = "llama.schema.json";
        Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(new File(schemaFileName).getPath());
        if (inputStream.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        assertTrue(generator.generate(inputStream.get(), Optional.empty()));
    }
}

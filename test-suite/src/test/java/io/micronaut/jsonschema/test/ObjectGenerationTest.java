package io.micronaut.jsonschema.test;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.generator.JsonRecordCreator;
import io.micronaut.sourcegen.model.PropertyDef;
import io.micronaut.sourcegen.model.RecordDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class ObjectGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @Test
    void objectBuilder() throws IOException {
        File jsonFile = new File("expected-llama.schema.json");
        var recordCreator = new JsonRecordCreator(resourceLoader);
        RecordDef recordDef = recordCreator.build(jsonFile);
        assertNotNull(recordDef);
        assertEquals("LlamaRecord", recordDef.getSimpleName());
        assertEquals(2, recordDef.getProperties().size());

        PropertyDef age = recordDef.getProperties().get(0);
        assertEquals("age", age.getName());
        assertEquals(TypeDef.Primitive.INT, age.getType());

        PropertyDef name = recordDef.getProperties().get(1);
        assertEquals("name", name.getName());
        assertEquals(TypeDef.STRING, name.getType());
    }

    @Test
    void objectGenerator() throws IOException {
        File jsonFile = new File("expected-llama.schema.json");
        var recordCreator = new JsonRecordCreator(resourceLoader);
        assertTrue(recordCreator.generate(jsonFile));
    }

}

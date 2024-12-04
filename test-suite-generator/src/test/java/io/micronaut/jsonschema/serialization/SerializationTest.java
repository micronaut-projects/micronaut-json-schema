package io.micronaut.jsonschema.serialization;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.jsonschema.generator.animals.Animal;
import io.micronaut.jsonschema.generator.animals.Cat;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@MicronautTest
public class SerializationTest {
    JsonMapper jsonMapper = new JsonMapper();

    @Test
    void testSerializeGeneratedCat(ObjectMapper objectMapper) throws IOException {
        var animal = jsonMapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Cat",
              "hasMate": true
            }
            """, Animal.class);
        assertEquals(Cat.class, animal.getClass());
        Cat cat = (Cat) animal;
        String result = objectMapper.writeValueAsString(cat);
        assertEquals("{\"id\":\"0x\",\"birthdate\":\"2000-01-01\",\"name\":\"Micronaut\",\"hasMate\":true}", result);
    }
}

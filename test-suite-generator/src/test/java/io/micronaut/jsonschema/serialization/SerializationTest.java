package io.micronaut.jsonschema.serialization;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.jsonschema.generator.animals.Animal;
import io.micronaut.jsonschema.generator.animals.Cat;
import io.micronaut.jsonschema.generator.animals.Dog;
import io.micronaut.jsonschema.generator.ref.Achievement;
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
        // put const fields at last
        String inputData = """
            {
              "resourceType": "Cat",
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "hasMate": true
            }
            """.replaceAll("\\s", "");
        var animal = jsonMapper.readValue(inputData, Animal.class);
        assertEquals(Cat.class, animal.getClass());
        Cat cat = (Cat) animal;
        String result = objectMapper.writeValueAsString(cat);
        assertEquals(inputData, result);
    }

    @Test
    void testSerializeGeneratedInnerEnum(ObjectMapper objectMapper) throws IOException {
        String inputData = """
            {
              "Type": "award",
              "Title": "This is title",
              "Date": "2000-01-01",
              "Website": "www.website.com",
              "Summary": "Summary of something"
            }
            """.replaceAll("\\s", "");
        var obj = jsonMapper.readValue(inputData, Achievement.class);

        String result = objectMapper.writeValueAsString(obj);
        assertEquals(inputData, result);
    }

    @Test
    void testSerializeGeneratedAdditionalProperty(ObjectMapper objectMapper) throws IOException {
        String inputData = """
            {
              "resourceType": "Dog",
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "nickname": "Goodie",
              "enemies": [
              {
                  "resourceType": "Cat",
                  "id": "1x",
                  "birthdate": "2000-01-01",
                  "name": "Pegasus",
                  "hasMate": false
              }, {
                  "resourceType": "Cat",
                  "id": "2x",
                  "birthdate": "2000-01-01",
                  "name": "Micro-Pego",
                  "hasMate": false
              }],
              "hasMate": true,
              "ownerName": "Owner"
            }
            """.replaceAll("\\s", "");
        var dog = jsonMapper.readValue(inputData, Dog.class);

        String result = objectMapper.writeValueAsString(dog);
        assertEquals(inputData, result);
    }
}

package io.micronaut.jsonschema.serialization

import io.micronaut.jsonschema.generator.animals.Animal
import io.micronaut.jsonschema.generator.animals.Cat
import io.micronaut.jsonschema.generator.animals.Dog
import io.micronaut.jsonschema.generator.ref.Achievement
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

@MicronautTest
class SerializationTest {

    val jsonMapper = JsonMapper()

    @Test
    fun testSerializeGeneratedCat(objectMapper: ObjectMapper) {
        // put const fields at last
        val inputData = """
            {
              "resourceType": "Cat",
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "hasMate": true
            }
            """.replace(Regex("\\s"), "")
        val animal = jsonMapper.readValue(inputData, Animal::class.java)
        assertEquals(Cat::class.java, animal.javaClass)
        val cat = animal as Cat
        val result = objectMapper.writeValueAsString(cat)
        assertEquals(inputData, result)
    }

    @Test
    fun testSerializeGeneratedInnerEnum(objectMapper: ObjectMapper) {
        val inputData = """
            {
              "Type": "award",
              "Title": "This is title",
              "Date": "2000-01-01",
              "Website": "www.website.com",
              "Summary": "Summary of something"
            }
            """.replace(Regex("\\s"), "")
        val obj = jsonMapper.readValue(inputData, Achievement::class.java)

        val result = objectMapper.writeValueAsString(obj)
        assertEquals(inputData, result)
    }

    // tag::serialization[]
    @Test
    fun testSerializeGeneratedAdditionalProperty(objectMapper: ObjectMapper) {
        val inputData = """
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
            """.replace(Regex("\\s"), "")
        val dog = jsonMapper.readValue(inputData, Dog::class.java)

        val result = objectMapper.writeValueAsString(dog)
        assertEquals(inputData, result)
    }
    // end::serialization[]
}

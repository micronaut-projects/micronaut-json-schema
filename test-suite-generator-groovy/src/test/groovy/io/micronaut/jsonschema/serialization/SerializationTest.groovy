package io.micronaut.jsonschema.serialization

import io.micronaut.jsonschema.generator.animals.Animal
import io.micronaut.jsonschema.generator.animals.Cat
import io.micronaut.jsonschema.generator.animals.Dog
import io.micronaut.jsonschema.generator.ref.Achievement
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

@MicronautTest
class SerializationTest extends Specification {

    JsonMapper jsonMapper = new JsonMapper()

    @Inject
    ObjectMapper objectMapper

    void "serialize generated cat"() {
        given: // put const fields at last
        String inputData = '''
            {
              "resourceType": "Cat",
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "hasMate": true
            }
            '''.replaceAll("\\s", "")

        when:
        def animal = jsonMapper.readValue(inputData, Animal)

        then:
        animal.class == Cat

        when:
        String result = objectMapper.writeValueAsString((Cat) animal)

        then:
        result == inputData
    }

    void "serialize generated inner enum"() {
        given:
        String inputData = '''
            {
              "Type": "award",
              "Title": "This is title",
              "Date": "2000-01-01",
              "Website": "www.website.com",
              "Summary": "Summary of something"
            }
            '''.replaceAll("\\s", "")

        when:
        def obj = jsonMapper.readValue(inputData, Achievement)
        String result = objectMapper.writeValueAsString(obj)

        then:
        result == inputData
    }

    // tag::serialization[]
    void "serialize generated additional property"() {
        given:
        String inputData = '''
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
            '''.replaceAll("\\s", "")

        when:
        def dog = jsonMapper.readValue(inputData, Dog)
        String result = objectMapper.writeValueAsString(dog)

        then:
        result == inputData
    }
    // end::serialization[]
}

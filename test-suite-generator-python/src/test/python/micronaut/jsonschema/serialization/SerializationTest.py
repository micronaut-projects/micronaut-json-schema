import re
from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.jsonschema.generator.ref import Achievement
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from tools.jackson.databind.json import JsonMapper

# TODO(python): java.type needed because importing io.micronaut.jsonschema.generator.animals writes its Python shim to
# micronaut/jsonschema/generator/animals/__init__.py, the source package of AnimalTest ("Output stream or writer has
# already been opened"); see DISABLED_TESTS.md.
Animal = java.type("io.micronaut.jsonschema.generator.animals.Animal")
Cat = java.type("io.micronaut.jsonschema.generator.animals.Cat")
Dog = java.type("io.micronaut.jsonschema.generator.animals.Dog")


@MicronautTest
class SerializationTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    json_mapper = JsonMapper()

    @Test
    def test_serialize_generated_cat(self) -> None:
        # put const fields at last
        input_data = re.sub(r"\s", "", """
            {
              "resourceType": "Cat",
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "hasMate": true
            }
            """)
        animal = self.json_mapper.readValue(input_data, Animal)
        assert animal.getClass() == Cat.class_
        result = self.object_mapper.writeValueAsString(animal)
        assert result == input_data

    @Test
    def test_serialize_generated_inner_enum(self) -> None:
        input_data = re.sub(r"\s", "", """
            {
              "Type": "award",
              "Title": "This is title",
              "Date": "2000-01-01",
              "Website": "www.website.com",
              "Summary": "Summary of something"
            }
            """)
        obj = self.json_mapper.readValue(input_data, Achievement)

        result = self.object_mapper.writeValueAsString(obj)
        assert result == input_data

    # tag::serialization[]
    @Test
    def test_serialize_generated_additional_property(self) -> None:
        input_data = re.sub(r"\s", "", """
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
            """)
        dog = self.json_mapper.readValue(input_data, Dog)

        result = self.object_mapper.writeValueAsString(dog)
        assert result == input_data
    # end::serialization[]

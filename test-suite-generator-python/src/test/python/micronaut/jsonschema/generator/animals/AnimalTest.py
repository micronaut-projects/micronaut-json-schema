import java
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from tools.jackson.databind.json import JsonMapper

# TODO(python): java.type needed because importing io.micronaut.jsonschema.generator.animals writes its Python shim to
# micronaut/jsonschema/generator/animals/__init__.py, the source package of this test ("Output stream or writer has
# already been opened"); see DISABLED_TESTS.md.
Animal = java.type("io.micronaut.jsonschema.generator.animals.Animal")
Cat = java.type("io.micronaut.jsonschema.generator.animals.Cat")
Dog = java.type("io.micronaut.jsonschema.generator.animals.Dog")
Fish = java.type("io.micronaut.jsonschema.generator.animals.Fish")
Human = java.type("io.micronaut.jsonschema.generator.animals.Human")


@MicronautTest(startApplication=False)
class AnimalTest:

    json_mapper = JsonMapper()

    @Test
    def test_map_animal(self) -> None:
        animal = self.json_mapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Animal"
            }
            """, Animal)
        assert animal.getClass() == Animal.class_
        assert animal.getId() == "0x"
        assert animal.getBirthdate() == "2000-01-01"
        assert animal.getName() == "Micronaut"

    # tag::mapp[]
    @Test
    def test_map_cat(self) -> None:
        animal = self.json_mapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Cat",
              "hasMate": true
            }
            """, Animal)
        assert animal.getClass() == Cat.class_
        cat = animal

        assert cat.getId() == "0x"
        assert cat.getBirthdate() == "2000-01-01"
        assert cat.getName() == "Micronaut"
        assert cat.getHasMate() is True
        assert cat.resourceType == "Cat"
    # end::mapp[]

    @Test
    def test_map_dog(self) -> None:
        animal = self.json_mapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Dog",
              "hasMate": true,
              "nickname": "Goodie",
              "ownerName": "Owner",
              "enemies": [
              {
                  "id": "1x",
                  "birthdate": "2000-01-01",
                  "name": "Pegasus",
                  "resourceType": "Cat"
              }, {
                  "id": "2x",
                  "birthdate": "2000-01-01",
                  "name": "Micro-Pego",
                  "resourceType": "Cat"
              }]
            }
            """, Animal)
        assert animal.getClass() == Dog.class_
        dog = animal

        assert dog.getId() == "0x"
        assert dog.getBirthdate() == "2000-01-01"
        assert dog.getName() == "Micronaut"
        assert dog.getHasMate() is True
        assert dog.resourceType == "Dog"
        assert dog.getNickname() == "Goodie"
        assert dog.getUnknownFields().getOrDefault("ownerName", "") == "Owner"
        assert dog.getEnemies().size() == 2

    @Test
    def test_map_fish(self) -> None:
        animal = self.json_mapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Fish",
              "ownerName": "Owner",
              "friends": [
              {
                  "id": "1x",
                  "birthdate": "2000-01-01",
                  "name": "Pegasus",
                  "resourceType": "Fish",
                  "ownerName": "Owner"
              }, {
                  "id": "2x",
                  "birthdate": "2000-01-01",
                  "name": "Micro-Pego",
                  "resourceType": "Fish",
                  "ownerName": "Owner"
              }]
            }
            """, Animal)
        assert animal.getClass() == Fish.class_
        fish = animal

        assert fish.getId() == "0x"
        assert fish.getBirthdate() == "2000-01-01"
        assert fish.getName() == "Micronaut"
        assert fish.resourceType == "Fish"
        assert fish.getUnknownFields().get("ownerName") == "Owner"
        assert fish.getFriends().size() == 2

    @Test
    def test_map_human(self) -> None:
        animal = self.json_mapper.readValue("""
            {
              "id": "N-XN",
              "birthdate": "2000-01-01",
              "name": "ABCDEFGHIJKLMNOPQRSTUVW",
              "resourceType": "Human",
              "firstName": "ABCDEFGHIJKLMNO",
              "lastName": "ABCD",
              "sport": "ABCDE"
            }
            """, Animal)
        assert animal.getClass() == Human.class_
        human = animal
        assert human.getId() == "N-XN"
        assert human.getBirthdate() == "2000-01-01"
        assert human.resourceType == "Human"
        assert human.getFirstName() == "ABCDEFGHIJKLMNO"

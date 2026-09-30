package io.micronaut.jsonschema.generator.animals

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class AnimalTest extends Specification {

    JsonMapper jsonMapper = new JsonMapper()

    void "map animal"() {
        when:
        def animal = jsonMapper.readValue('''
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Animal"
            }
            ''', Animal)

        then:
        animal.class == Animal
        animal.id == "0x"
        animal.birthdate == "2000-01-01"
        animal.name == "Micronaut"
    }

    // tag::mapp[]
    void "map cat"() {
        when:
        def animal = jsonMapper.readValue('''
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Cat",
              "hasMate": true
            }
            ''', Animal)

        then:
        animal.class == Cat
        Cat cat = (Cat) animal
        cat.id == "0x"
        cat.birthdate == "2000-01-01"
        cat.name == "Micronaut"
        cat.hasMate
        cat.resourceType == "Cat"
    }
    // end::mapp[]

    void "map dog"() {
        when:
        def animal = jsonMapper.readValue('''
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
            ''', Animal)

        then:
        animal.class == Dog
        Dog dog = (Dog) animal
        dog.id == "0x"
        dog.birthdate == "2000-01-01"
        dog.name == "Micronaut"
        dog.hasMate
        dog.resourceType == "Dog"
        dog.nickname == "Goodie"
        dog.unknownFields.getOrDefault("ownerName", "") == "Owner"
        dog.enemies.size() == 2
    }

    void "map fish"() {
        when:
        def animal = jsonMapper.readValue('''
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
            ''', Animal)

        then:
        animal.class == Fish
        Fish fish = (Fish) animal
        fish.id == "0x"
        fish.birthdate == "2000-01-01"
        fish.name == "Micronaut"
        fish.resourceType == "Fish"
        fish.unknownFields.get("ownerName") == "Owner"
        fish.friends.size() == 2
    }

    void "map human"() {
        when:
        def animal = jsonMapper.readValue('''
            {
              "id": "N-XN",
              "birthdate": "2000-01-01",
              "name": "ABCDEFGHIJKLMNOPQRSTUVW",
              "resourceType": "Human",
              "firstName": "ABCDEFGHIJKLMNO",
              "lastName": "ABCD",
              "sport": "ABCDE"
            }
            ''', Animal)

        then:
        animal.class == Human
        Human human = (Human) animal
        human.id == "N-XN"
        human.birthdate == "2000-01-01"
        human.resourceType == "Human"
        human.firstName == "ABCDEFGHIJKLMNO"
    }
}

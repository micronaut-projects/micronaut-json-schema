package io.micronaut.jsonschema.generator.animals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AnimalTest {

    val jsonMapper = JsonMapper()

    @Test
    fun mapAnimal() {
        val animal = jsonMapper.readValue(
            """
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Animal"
            }
            """, Animal::class.java
        )
        assertEquals(Animal::class.java, animal.javaClass)
        assertEquals("0x", animal.id)
        assertEquals("2000-01-01", animal.birthdate)
        assertEquals("Micronaut", animal.name)
    }

    // tag::mapp[]
    @Test
    fun mapCat() {
        val animal = jsonMapper.readValue(
            """
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Cat",
              "hasMate": true
            }
            """, Animal::class.java
        )
        assertEquals(Cat::class.java, animal.javaClass)
        val cat = animal as Cat

        assertEquals("0x", cat.id)
        assertEquals("2000-01-01", cat.birthdate)
        assertEquals("Micronaut", cat.name)
        assertEquals(true, cat.hasMate)
        assertEquals("Cat", cat.resourceType)
    }
    // end::mapp[]

    @Test
    fun mapDog() {
        val animal = jsonMapper.readValue(
            """
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
            """, Animal::class.java
        )
        assertEquals(Dog::class.java, animal.javaClass)
        val dog = animal as Dog

        assertEquals("0x", dog.id)
        assertEquals("2000-01-01", dog.birthdate)
        assertEquals("Micronaut", dog.name)
        assertEquals(true, dog.hasMate)
        assertEquals("Dog", dog.resourceType)
        assertEquals("Goodie", dog.nickname)
        assertEquals("Owner", dog.unknownFields.getOrDefault("ownerName", ""))
        assertEquals(2, dog.enemies.size)
    }

    @Test
    fun mapFish() {
        val animal = jsonMapper.readValue(
            """
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
            """, Animal::class.java
        )
        assertEquals(Fish::class.java, animal.javaClass)
        val fish = animal as Fish

        assertEquals("0x", fish.id)
        assertEquals("2000-01-01", fish.birthdate)
        assertEquals("Micronaut", fish.name)
        assertEquals("Fish", fish.resourceType)
        assertEquals("Owner", fish.unknownFields["ownerName"])
        assertEquals(2, fish.friends.size)
    }

    @Test
    fun mapHuman() {
        val animal = jsonMapper.readValue(
            """
            {
              "id": "N-XN",
              "birthdate": "2000-01-01",
              "name": "ABCDEFGHIJKLMNOPQRSTUVW",
              "resourceType": "Human",
              "firstName": "ABCDEFGHIJKLMNO",
              "lastName": "ABCD",
              "sport": "ABCDE"
            }
            """, Animal::class.java
        )
        assertEquals(Human::class.java, animal.javaClass)
        val human = animal as Human
        assertEquals("N-XN", human.id)
        assertEquals("2000-01-01", human.birthdate)
        assertEquals("Human", human.resourceType)
        assertEquals("ABCDEFGHIJKLMNO", human.firstName)
    }
}

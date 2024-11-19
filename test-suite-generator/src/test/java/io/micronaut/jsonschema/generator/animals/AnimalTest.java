/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.generator.animals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class AnimalTest {

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void mapAnimal() throws JsonProcessingException {
        var animal = jsonMapper.readValue("""
            {
              "id": "0x",
              "birthdate": "2000-01-01",
              "name": "Micronaut",
              "resourceType": "Animal"
            }
            """, Animal.class);
        assertEquals(Animal.class, animal.getClass());
        assertEquals("0x", animal.getId());
        assertEquals("2000-01-01", animal.getBirthdate());
        assertEquals("Micronaut", animal.getName());
    }

    @Test
    public void mapCat() throws JsonProcessingException {
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

        assertEquals("0x", cat.getId());
        assertEquals("2000-01-01", cat.getBirthdate());
        assertEquals("Micronaut", cat.getName());
        assertEquals(true, cat.getHasMate());
        assertEquals("Cat", cat.resourceType);
    }

    @Test
    public void mapDog() throws JsonProcessingException {
        var animal = jsonMapper.readValue("""
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
            """, Animal.class);
        assertEquals(Dog.class, animal.getClass());
        Dog dog = (Dog) animal;

        assertEquals("0x", dog.getId());
        assertEquals("2000-01-01", dog.getBirthdate());
        assertEquals("Micronaut", dog.getName());
        assertEquals(true, dog.getHasMate());
        assertEquals("Dog", dog.resourceType);
        assertEquals("Goodie", dog.getNickname());
        assertEquals("Owner", dog.getUnknownFields().get("ownerName"));
        assertEquals(2, dog.getEnemies().size());
    }

    @Test
    public void mapFish() throws JsonProcessingException {
        var animal = jsonMapper.readValue("""
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
            """, Animal.class);
        assertEquals(Fish.class, animal.getClass());
        Fish fish = (Fish) animal;

        assertEquals("0x", fish.getId());
        assertEquals("2000-01-01", fish.getBirthdate());
        assertEquals("Micronaut", fish.getName());
        assertEquals("Fish", fish.resourceType);
        assertEquals("Owner", fish.otherFields().get("ownerName"));
        assertEquals(2, fish.getFriends().size());
    }

    @Test
    public void mapHuman() throws JsonProcessingException {
        var animal = jsonMapper.readValue("""
            {
              "id": "N-XN",
              "birthdate": "2000-01-01",
              "name": "ABCDEFGHIJKLMNOPQRSTUVW",
              "resourceType": "Human",
              "firstName": "ABCDEFGHIJKLMNO",
              "lastName": "ABCD",
              "sport": "ABCDE"
            }
            """, Animal.class);
        assertEquals(Human.class, animal.getClass());
        Human human = (Human) animal;
        assertEquals("N-XN", human.getId());
        assertEquals("2000-01-01", human.getBirthdate());
        assertEquals("Human", human.resourceType);
        assertEquals("ABCDEFGHIJKLMNO", human.getFirstName());
    }
    // TODO: enum test
}

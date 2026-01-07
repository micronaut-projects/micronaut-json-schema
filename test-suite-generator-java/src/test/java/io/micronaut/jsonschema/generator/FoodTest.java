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
package io.micronaut.jsonschema.generator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import io.micronaut.jsonschema.generator.food.Food;
import io.micronaut.jsonschema.generator.food.Fruit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FoodTest {

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void mapFood() throws JacksonException {
        var food = jsonMapper.readValue("""
            {
              "fruits": {
              "fruits":
              [{
                "fruitName": "Strawberry",
                "fruitColor": "Pink",
                "fruitTaste": "sweet"
              },{
                "fruitName": "Banana",
                "fruitColor": "Yellow",
                "fruitTaste": "sweet"
              },{
                "fruitName": "Kiwi",
                "fruitColor": "Green",
                "fruitTaste": "sour",
                "fruitSeason": "summer"
              }]
              },
              "favouriteFruit": {
                "fruitName": "Strawberry",
                "fruitColor": "Pink",
                "fruitTaste": "sweet"
              },
              "vegetables": [{
                "veggieName": "carrot",
                "veggieLike": true
              }]
            }
            """, Food.class);
        assertEquals(Food.class, food.getClass());
        assertEquals(3, food.fruits().fruits().size());
        assertEquals(1, food.vegetables().size());

        assertEquals("sweet", food.favouriteFruit().fruitTaste().getValue());
        assertEquals(Fruit.FruitTaste.statusOf("sour"), Fruit.FruitTaste.SOUR);
        assertNull(Fruit.FruitTaste.statusOf("umami"));
    }
}

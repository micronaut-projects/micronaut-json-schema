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
package io.micronaut.jsonschema.generator.llama;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class LlamaTest {

    @Test
    public void test() {
        var hours = List.of(List.of(12f, 13f), List.of(14f, 15f));
        Llama llama = new Llama(3, "spitz", Llama.Status.SINGLE, hours);

        assertEquals(3, llama.age());
        assertEquals("spitz", llama.name());
        assertEquals(Llama.Status.SINGLE, llama.status());
        assertEquals(hours, llama.hours());
    }
}

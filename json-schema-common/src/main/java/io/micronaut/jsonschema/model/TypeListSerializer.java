/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

@Internal
public class TypeListSerializer extends JsonSerializer<List<Schema.Type>> {

    @Override
    public void serialize(List<Schema.Type> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (CollectionUtils.isEmpty(value)) {
            gen.writeNull();
        } else if (value.size() == 1) {
            gen.writeString(value.get(0).value());
        } else {
            gen.writeStartArray();
            for (Schema.Type t : value) {
                gen.writeString(t.value());
            }
            gen.writeEndArray();
        }
    }
}


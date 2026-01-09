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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;

import java.util.List;

@Internal
class TypeListSerializer extends ValueSerializer<List<Schema.Type>> {

    @Override
    public void serialize(List<Schema.Type> value, JsonGenerator gen, SerializationContext serializers) throws JacksonException {
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


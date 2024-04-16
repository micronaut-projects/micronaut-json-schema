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
package io.micronaut.jsonschema.visitor.serialization;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.type.SimpleType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.visitor.model.Schema;

import java.io.IOException;

/**
 * A factory of mappers for swagger serialization and deserialization.
 */
@Internal
public class JsonSchemaMapperFactory {

    /**
     * Create a JSON object mapper.
     *
     * @return A JSON object mapper
     */
    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        SimpleModule module = new SimpleModule();
        module.addSerializer(Schema.class, new SchemaSerializer());
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                if (beanDesc.getBeanClass() == Schema.class) {
                    return new SchemaDeserializer(deserializer);
                }
                return deserializer;
            }
        });
        mapper.registerModule(module);

        return mapper;
    }

    static class SchemaSerializer extends JsonSerializer<Schema> {
        @Override
        public void serialize(Schema schema, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
            if (schema == Schema.TRUE) {
                jsonGenerator.writeBoolean(true);
            } else if (schema == Schema.FALSE) {
                jsonGenerator.writeBoolean(false);
            } else {
                BeanSerializerFactory.instance.createSerializer(provider, SimpleType.construct(Schema.class))
                    .serialize(schema, jsonGenerator, provider);
            }
        }
    }

    static class SchemaDeserializer extends DelegatingDeserializer {

        public SchemaDeserializer(JsonDeserializer delegate) {
            super(delegate);
        }

        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> delegate) {
            return new SchemaDeserializer(delegate);
        }

        @Override
        public Schema deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JacksonException {
            JsonNode tree = jsonParser.getCodec().readTree(jsonParser);
            jsonParser.finishToken();
            if (tree.isObject()) {
                JsonParser newParser = new TreeTraversingParser(tree, jsonParser.getCodec());
                newParser.nextToken();
                try {
                    return (Schema) getDelegatee().deserialize(newParser, context);
                } catch (Exception e) {
                    System.out.println(e);
                }
            } else if (tree instanceof BooleanNode bool) {
                return bool.asBoolean() ? Schema.TRUE : Schema.FALSE;
            }
            return null;
        }
    }
}

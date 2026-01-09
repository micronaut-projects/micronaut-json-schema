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
package io.micronaut.jsonschema.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.databind.node.TreeTraversingParser;
import tools.jackson.databind.ser.BeanSerializerFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.model.Schema;

import java.util.Collections;

/**
 * A factory of mappers for json schema serialization and deserialization.
 */
@Internal
public class JsonSchemaMapperFactory {

    /**
     * Create a JSON object mapper.
     *
     * @return A JSON object mapper
     */
    public static ObjectMapper createMapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Schema.class, new SchemaSerializer());
        module.setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier beanDesc, ValueDeserializer<?> deserializer) {
                if (beanDesc.getBeanClass() == Schema.class) {
                    return new SchemaDeserializer(deserializer);
                }
                return deserializer;
            }
        });

        ObjectMapper mapper = JsonMapper.builder()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .changeDefaultPropertyInclusion(incl ->
                incl.withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .addModule(module)
            .build();

        return mapper;
    }

    static class SchemaSerializer extends ValueSerializer<Schema> {
        @Override
        public void serialize(Schema schema, JsonGenerator jsonGenerator, SerializationContext provider) throws JacksonException {
            if (schema == Schema.TRUE) {
                jsonGenerator.writeBoolean(true);
            } else if (schema == Schema.FALSE) {
                jsonGenerator.writeBoolean(false);
            } else {
                BeanSerializerFactory.instance.createSerializer(provider, provider.getTypeFactory().constructType(Schema.class))
                    .serialize(schema, jsonGenerator, provider);
            }
        }
    }

    static class SchemaDeserializer extends DelegatingDeserializer {

        public SchemaDeserializer(ValueDeserializer delegate) {
            super(delegate);
        }

        @Override
        protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> delegate) {
            return new SchemaDeserializer(delegate);
        }

        @Override
        public Schema deserialize(JsonParser jsonParser, DeserializationContext context) throws JacksonException {
            JsonNode tree = jsonParser.objectReadContext().readTree(jsonParser);
            jsonParser.finishToken();
            if (tree instanceof ObjectNode node) {
                // An empty schema is a true schema, as there is nothing to validate
                if (node.isEmpty()) {
                    return Schema.TRUE;
                }
                // Type is always stored as an array, convert it
                if (node.get("type") instanceof StringNode text) {
                    node.set("type",
                        new ArrayNode(context.getNodeFactory(), Collections.singletonList(text))
                    );
                }
                try (JsonParser newParser = new TreeTraversingParser(tree, jsonParser.objectReadContext())) {
                    newParser.nextToken();
                    return (Schema) getDelegatee().deserialize(newParser, context);
                }
            } else if (tree instanceof BooleanNode bool) {
                return bool.asBoolean() ? Schema.TRUE : Schema.FALSE;
            }
            return null;
        }
    }
}

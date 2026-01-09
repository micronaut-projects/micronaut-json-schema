package model

import tools.jackson.databind.ObjectMapper
import io.micronaut.jsonschema.model.Schema
import spock.lang.Specification

class TypeListSerializerSpec extends Specification {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    void serializeNoTypeToNull() {
        when:
        Schema schema = new Schema()
        String json = objectMapper.writeValueAsString(schema)

        then:
        noExceptionThrown()
        json.contains("\"type\":null")
    }

    void serializeSingleType() {
        when:
        Schema schema = new Schema()
        schema.setType(List.of(Schema.Type.OBJECT))
        String json = objectMapper.writeValueAsString(schema)

        then:
        noExceptionThrown()
        json.contains("\"type\":\"object\"")
    }

    void serializeMultipleItemType() {
        when:
        Schema schema = new Schema()
        schema.setType(List.of(Schema.Type.STRING,Schema.Type.NUMBER))
        String json = objectMapper.writeValueAsString(schema)

        then:
        noExceptionThrown()
        json.contains("\"type\":[\"string\",\"number\"]")
    }
}

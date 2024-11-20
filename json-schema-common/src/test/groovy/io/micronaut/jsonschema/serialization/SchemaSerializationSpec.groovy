package io.micronaut.jsonschema.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.jsonschema.model.Schema
import spock.lang.Specification

class SchemaSerializationSpec extends Specification {

    private final ObjectMapper mapper = JsonSchemaMapperFactory.createMapper()

    void "test simple schema deserialization"() {
        when:
        Schema schema = mapper.readValue('{"type":["number"],"minimum":12,"maximum":13}', Schema.class)

        then:
        schema.type == [Schema.Type.NUMBER]
        schema.minimum == 12
        schema.maximum == 13

        when:
        schema = mapper.readValue('{"type":["object"],"properties":{"a":{"type":["integer"]}}}', Schema.class)

        then:
        schema.type == [Schema.Type.OBJECT]
        schema.properties.size() == 1
        schema.properties["a"].type == [Schema.Type.INTEGER]
    }

    void "test type deserialization"() {
        when:
        Schema schema = mapper.readValue(json, Schema.class)

        then:
        schema.type == expectedType

        where:
        json                           | expectedType
        '{"type":"number"}'            | Schema.number().type
        '{"type":["number","null"]}'   | Schema.number().addType(Schema.Type.NULL).type
        '{"type":["number","string"]}' | [Schema.Type.NUMBER, Schema.Type.STRING]
    }

    void "test #json deserialization"() {
        when:
        Schema schema = mapper.readValue(json, Schema.class)

        then:
        schema == expectedSchema

        where:
        json    | expectedSchema
        '{}'    | Schema.TRUE
        'true'  | Schema.TRUE
        'false' | Schema.FALSE
    }

    void "test definitions deserialization"() {
        when:
        Schema schema = mapper.readValue('{"definitions":{"a":{"type":"number"}}}', Schema.class)

        then:
        schema.get$defs().size() == 1
        schema.get$defs().get("a").getType() == [Schema.Type.NUMBER]

        when:
        schema = mapper.readValue('{"$defs":{"b":{"type":"string"}}}', Schema.class)

        then:
        schema.get$defs().size() == 1
        schema.get$defs().get("b").getType() == [Schema.Type.STRING]
    }

    void "test simple schema serialization"() {
        when:
        var json = mapper.writeValueAsString(schema)

        then:
        json == expectedJson

        where:
        schema                                             | expectedJson
        Schema.number().setMinimum(12).setMaximum(13)      | '{"type":["number"],"maximum":13,"minimum":12}'
        Schema.object().putProperty("a", Schema.integer()) | '{"type":["object"],"properties":{"a":{"type":["integer"]}}}'
    }

    void "test #expectedJson serialization"() {
        when:
        var json = mapper.writeValueAsString(schema)

        then:
        json == expectedJson

        where:
        expectedJson | schema
        'true'       | Schema.TRUE
        'false'      | Schema.FALSE
    }

}

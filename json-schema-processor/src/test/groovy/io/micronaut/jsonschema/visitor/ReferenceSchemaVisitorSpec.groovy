package io.micronaut.jsonschema.visitor

import io.micronaut.jsonschema.model.Schema


class ReferenceSchemaVisitorSpec extends AbstractJsonSchemaSpec {

    void "self-referencing schema"() {
        given:
        def schema = buildJsonSchema('test.Possum', 'possum', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.List;

        @JsonSchema
        public record Possum(
                List<Possum> children
        ) {
        }
""")

        expect:
        schema.title == "Possum"
        schema.properties.size() == 1
        schema.properties['children'].type == [Schema.Type.ARRAY]
        schema.properties['children'].items.$ref == 'classpath:META-INF/schemas/possum.schema.json'
    }

    void "schema reference"() {
        given:
        def schema = buildJsonSchema('test.Player', 'player', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.List;

        @JsonSchema
        public record Player(
                String name,
                Position pos
        ) {
        }

        @JsonSchema
        record Position(
                double x,
                double y
        ) {
        }


""")

        expect:
        schema.title == "Player"
        schema.properties.size() == 2
        schema.properties['name'].type == [Schema.Type.STRING]
        schema.properties['pos'].$ref == 'classpath:META-INF/schemas/position.schema.json'
    }

    void "property schema reference"() {
        given:
        def schema = buildJsonSchema('test.Player', 'player', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.List;

        @JsonSchema
        public record Player(
                String name,
                @JsonSchema(uri = "/position") Object pos
        ) {
        }
""")

        expect:
        schema.title == "Player"
        schema.properties.size() == 2
        schema.properties['name'].type == [Schema.Type.STRING]
        schema.properties['pos'].$ref == 'classpath:META-INF/schemas/position.schema.json'
    }


    void "nested schema reference"() {
        given:
        def schema = buildJsonSchema('test.Order', 'order', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.List;

        @JsonSchema
        public record Order(
                List<Line> lines
        ) {

            @JsonSchema
            public record Line(
                    String product
            ) {
            }
        }
""")

        expect:
        schema.$id == 'classpath:META-INF/schemas/order.schema.json'
        schema.properties['lines'].items.$ref == 'classpath:META-INF/schemas/order-line.schema.json'
    }

}

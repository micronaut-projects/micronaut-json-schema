package io.micronaut.jsonschema.visitor

import io.micronaut.jsonschema.visitor.model.Schema


class JacksonJsonSchemaVisitorSpec extends AbstractJsonSchemaSpec {

    void "schema with jackson property annotations"() {
        given:
        def schema = buildJsonSchema('test.Cow', 'cow', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;

        @JsonSchema
        public record Cow(
                String name,
                @JsonIgnore
                int age,
                @JsonProperty("weight (kg)")
                double weight
        ) {
        }
""")

        expect:
        schema.title == "Cow"
        schema.properties.size() == 2
        schema.properties['name'].type == [Schema.Type.STRING]
        schema.properties['age'] == null
        schema.properties['weight (kg)'].type == [Schema.Type.NUMBER]
    }

    void "schema with subtypes"() {
        given:
        def schema = buildJsonSchema('test.Reptile', 'reptile', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;

        @JsonSchema
        @JsonTypeInfo(%s)
        @JsonSubTypes({
                @JsonSubTypes.Type(value = Salamander.class, name = "salamander"),
                @JsonSubTypes.Type(value = Alligator.class, name = "alligator")
        })
        public interface Reptile {
        }

        record Salamander(
                String name,
                int age
        ) implements Reptile {
        }

        record Alligator(
                String name,
                float length
        ) implements Reptile {
        }
""", typeInfoParams)

        expect:
        schema.title == "Reptile"
        schema.properties == null
        schema.oneOf.size() == 2

        schema.oneOf[0].title == 'Salamander'
        schema.oneOf[0].type == [Schema.Type.OBJECT]
        schema.oneOf[0].properties[propertyName].type == [Schema.Type.STRING]
        schema.oneOf[0].properties[propertyName].constValue == salamanderName
        schema.oneOf[0].properties["name"].type == [Schema.Type.STRING]
        schema.oneOf[0].properties["age"].type == [Schema.Type.INTEGER]

        schema.oneOf[1].title == 'Alligator'
        schema.oneOf[1].type == [Schema.Type.OBJECT]
        schema.oneOf[1].properties[propertyName].type == [Schema.Type.STRING]
        schema.oneOf[1].properties[propertyName].constValue == alligatorName
        schema.oneOf[1].properties["name"].type == [Schema.Type.STRING]
        schema.oneOf[1].properties["length"].type == [Schema.Type.NUMBER]

        where:
        typeInfoParams                                    | propertyName | salamanderName    | alligatorName
        "use = JsonTypeInfo.Id.CLASS"                     | "@class"     | "test.Salamander" | "test.Alligator"
        "use = JsonTypeInfo.Id.MINIMAL_CLASS"             | "@c"         | ".Salamander"     | ".Alligator"
        "use = JsonTypeInfo.Id.NAME"                      | "@type"      | "salamander"      | "alligator"
        "use = JsonTypeInfo.Id.NAME, property = \"name\"" | "name"       | "salamander"      | "alligator"
    }

    void "schema with referenced subtypes"() {
        given:
        def schema = buildJsonSchema('test.Reptile', 'reptile', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;

        @JsonSchema
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        @JsonSubTypes({
                @JsonSubTypes.Type(value = Salamander.class, name = "salamander"),
                @JsonSubTypes.Type(value = Alligator.class, name = "alligator")
        })
        public interface Reptile {
        }

        @JsonSchema
        record Salamander(
                String name,
                int age
        ) implements Reptile {
        }

        @JsonSchema
        record Alligator(
                String name,
                float length
        ) implements Reptile {
        }
""")

        expect:
        schema.title == "Reptile"
        schema.properties == null
        schema.oneOf.size() == 2

        schema.oneOf[0].$ref == '/salamander'
        schema.oneOf[1].$ref == '/alligator'
    }

}

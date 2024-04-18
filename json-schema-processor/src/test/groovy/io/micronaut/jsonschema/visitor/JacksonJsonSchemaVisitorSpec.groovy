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

    void "schema with subtypes with @JsonTypeInfo(include=WRAPPER_OBJECT)"() {
        given:
        def schema = buildJsonSchema('test.Reptile', 'reptile', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;

        @JsonSchema
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
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
""")

        expect:
        schema.title == "Reptile"
        schema.properties == null
        schema.oneOf.size() == 2

        schema.oneOf[0].type == [Schema.Type.OBJECT]
        schema.oneOf[0].properties["test.Salamander"].type == [Schema.Type.OBJECT]
        schema.oneOf[0].properties["test.Salamander"].title == 'Salamander'
        schema.oneOf[0].properties["test.Salamander"].properties["name"].type == [Schema.Type.STRING]
        schema.oneOf[0].properties["test.Salamander"].properties["age"].type == [Schema.Type.INTEGER]

        schema.oneOf[1]
        schema.oneOf[1].type == [Schema.Type.OBJECT]
        schema.oneOf[1].properties["test.Alligator"].type == [Schema.Type.OBJECT]
        schema.oneOf[1].properties["test.Alligator"].title == 'Alligator'
        schema.oneOf[1].properties["test.Alligator"].properties["name"].type == [Schema.Type.STRING]
        schema.oneOf[1].properties["test.Alligator"].properties["length"].type == [Schema.Type.NUMBER]
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

        schema.oneOf[0].$ref == 'http://localhost:8080/schemas/salamander.schema.json'
        schema.oneOf[1].$ref == 'http://localhost:8080/schemas/alligator.schema.json'
    }

    void "schema with JsonIgnore"() {
        given:
        def schema = buildJsonSchema('test.Elephant', 'elephant', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public record Elephant (
                @JsonIgnore
                int age,
                double height,
                double weight,
                @JsonIgnore
                String personality
        ) {
        }
""")

        expect:
        schema.title == "Elephant"
        schema.properties.size() == 2
        schema.properties['height'].type == [Schema.Type.NUMBER]
        schema.properties['weight'].type == [Schema.Type.NUMBER]
    }

    void "schema with JsonIgnoreType"() {
        given:
        def schema = buildJsonSchema('test.Elephant', 'elephant', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public record Elephant (
                @JsonIgnore
                int age,
                String name,
                Position position
        ) {
        }

        @JsonIgnoreType
        record Position(int x, int y) {}
""")

        expect:
        schema.title == "Elephant"
        schema.properties.size() == 1
        schema.properties['name'].type == [Schema.Type.STRING]
    }

    void "schema with JsonIgnoreProperties"() {
        given:
        def schema = buildJsonSchema('test.Elephant', 'elephant', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        @JsonIgnoreProperties({"age", "height"})
        public record Elephant (
                @JsonInclude // include cannot retrun an ignored property
                int age,
                double height,
                double weight,
                @JsonIgnore
                String personality
        ) {
        }
""")

        expect:
        schema.title == "Elephant"
        schema.properties.size() == 1
        schema.properties['weight'].type == [Schema.Type.NUMBER]
    }

    void "schema with JsonIncludeProperties"() {
        given:
        def schema = buildJsonSchema('test.Elephant', 'elephant', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        @JsonIncludeProperties({"age", "height"})
        public record Elephant (
                @JsonIgnore // ignore overrides inclusion
                int age,
                double height,
                double weight,
                @JsonInclude
                String personality
        ) {
        }
""")

        expect:
        schema.title == "Elephant"
        schema.properties.size() == 2
        schema.properties['height'].type == [Schema.Type.NUMBER]
        schema.properties['personality'].type == [Schema.Type.STRING]
    }

    void "schema with AnyGetter and AnySetter"() {
        given:
        def schema = buildJsonSchema('test.Dolphin', 'dolphin', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public class Dolphin {

            private Map<String, String> food = new HashMap<>();

            %s
            public void setFood(String key, String value) {
                food.put(key, value);
            }

            %s
            public Map<String, String> getFood() {
                return food;
            }

        }
""", setterAnn, getterAnn)

        expect:
        schema.title == "Dolphin"
        schema.additionalProperties.type == [Schema.Type.STRING]

        where:
        setterAnn        | getterAnn
        "@JsonAnySetter" | ""
        ""               | "@JsonAnyGetter"
        "@JsonAnySetter" | "@JsonAnyGetter"
    }

    void "schema with AnyGetter and AnySetter"() {
        given:
        def schema = buildJsonSchema('test.Dolphin', 'dolphin', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public class Dolphin {

            private Map<String, String> food = new HashMap<>();

            %s
            public void setFood(String key, String value) {
                food.put(key, value);
            }

            %s
            public Map<String, String> getFood() {
                return food;
            }

        }
""", setterAnn, getterAnn)

        expect:
        schema.title == "Dolphin"
        schema.additionalProperties.type == [Schema.Type.STRING]

        where:
        setterAnn        | getterAnn
        "@JsonAnySetter" | ""
        ""               | "@JsonAnyGetter"
        "@JsonAnySetter" | "@JsonAnyGetter"
    }

    void "schema with JsonUnwrapped"() {
        given:
        def schema = buildJsonSchema('test.Whale', 'whale', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        record Aquatic(
                float finLength,
                double speed
        ) {
        }

        @JsonSchema
        public record Whale (
                String color,
                double weight,
                @JsonUnwrapped
                Aquatic otherProperties
        ) {
        }
""")
        expect:
        schema.title == "Whale"
        schema.properties.size() == 4
        schema.properties['color'].type == [Schema.Type.STRING]
        schema.properties['weight'].type == [Schema.Type.NUMBER]
        schema.properties['finLength'].type == [Schema.Type.NUMBER]
        schema.properties['speed'].type == [Schema.Type.NUMBER]
    }

    void "schema with JsonGetter and JsonSetter"() {
        given:
        def schema = buildJsonSchema('test.Turtle', 'turtle', """
        package test;

        import com.fasterxml.jackson.annotation.*;
        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public class Turtle {

            private int age;

            @JsonGetter("speciesName")
            public String getSpecies() {
                return "turtle";
            }

            @JsonSetter
            public void setIsLizard(boolean isLizard) {
            }

            public int getAge() {
                return age;
            }

            public void setAge(int age) {
                this.age = age;
            }

        }
""")

        expect:
        schema.title == "Turtle"
        schema.properties.size() == 3
        schema.properties['speciesName'].type == [Schema.Type.STRING]
        schema.properties['age'].type == [Schema.Type.INTEGER]
        schema.properties['isLizard'].type == [Schema.Type.BOOLEAN]
    }

}

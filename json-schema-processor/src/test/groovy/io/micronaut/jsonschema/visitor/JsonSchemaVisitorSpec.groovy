package io.micronaut.jsonschema.visitor

import io.micronaut.jsonschema.visitor.model.Schema

class JsonSchemaVisitorSpec extends AbstractJsonSchemaSpec {

    void "simple record schema"() {
        given:
        def schema = buildJsonSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public record Salamander(
                String name,
                int age,
                Color color,
                List<String> environments,
                Map<String, List<String>> complexMap
        ) {
        }

        enum Color {
            RED,
            GREEN,
            BLUE
        }
""")

        expect:
        schema.title == "Salamander"
        schema.properties['name'].type == [Schema.Type.STRING]
        schema.properties['age'].type == [Schema.Type.INTEGER]
        schema.properties['color'].type == [Schema.Type.STRING]
        schema.properties['color'].enumValues == ["RED", "GREEN", "BLUE"]
        schema.properties['environments'].type == [Schema.Type.ARRAY]
        schema.properties['environments'].items.type == [Schema.Type.STRING]
        schema.properties['complexMap'].type == [Schema.Type.OBJECT]
        schema.properties['complexMap'].additionalProperties.type == [Schema.Type.ARRAY]
        schema.properties['complexMap'].additionalProperties.items.type == [Schema.Type.STRING]
    }

    void "types schema test"() {
        given:
        def schema = buildJsonSchema('test.Single', 'single', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public record Single(
                %s value
        ) {
        }
""", type)

        expect:
        schema.title == "Single"
        schema.properties['value'] != null
        check(schema.properties['value'])

        where:
        type                      | check
        "java.lang.Number"        | (p) -> p.type == [Schema.Type.NUMBER]
        "java.math.BigInteger"    | (p) -> p.type == [Schema.Type.INTEGER]
        "java.math.BigDecimal"    | (p) -> p.type == [Schema.Type.NUMBER]
        "java.time.ZonedDateTime" | (p) -> p.type == [Schema.Type.STRING] && p.format == "date-time"
        "java.time.Instant"       | (p) -> p.type == [Schema.Type.STRING] && p.format == "date-time"
        "java.time.LocalDate"     | (p) -> p.type == [Schema.Type.STRING] && p.format == "date"
        "java.time.OffsetTime"    | (p) -> p.type == [Schema.Type.STRING] && p.format == "time"
        "java.time.Duration"      | (p) -> p.type == [Schema.Type.STRING] && p.format == "duration"
        "java.util.Date"          | (p) -> p.type == [Schema.Type.STRING] && p.format == "date-time"
        "List<String>"            | (p) -> p.type == [Schema.Type.ARRAY] && !p.uniqueItems
        "Set<String>"             | (p) -> p.type == [Schema.Type.ARRAY] && p.uniqueItems
        "Map<String, String>"     | (p) -> p.type == [Schema.Type.OBJECT] && p.additionalProperties.type == [Schema.Type.STRING]
    }

    void "simple record customized schema"() {
        given:
        def schema = buildJsonSchema('test.GreenSalamander', 'dark-green-salamander', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema(
                title = "DarkGreenSalamander",
                description = "A dark green salamander",
                uri = "https://example.com/schemas/dark-green-salamander.schema.json"
        )
        public record GreenSalamander(
        ) {
        }
""")

        expect:
        schema.title == "DarkGreenSalamander"
        schema.description == "A dark green salamander"
        schema.$id == "https://example.com/schemas/dark-green-salamander.schema.json"
        schema.$schema != null
    }

    void "simple record with configuration schema"() {
        given:
        def options = ["baseUri": "https://example.com/schemas"]
        def schema = buildJsonSchema('test.Salamander', 'green-salamander', """
        package test;

        import io.micronaut.jsonschema.*;
        import java.util.*;

        @JsonSchema(title = "GreenSalamander")
        public record Salamander(
        ) {
        }
""", options)

        expect:
        schema.title == "GreenSalamander"
        schema.$id == "https://example.com/schemas/green-salamander.schema.json"
        schema.$schema != null
    }

    void "simple record with configuration schema and uri"() {
        given:
        def config = ["baseUri": "https://example.com/schemas"]
        def schema = buildJsonSchema('test.Salamander', 'salamander/green-salamander', """
        package test;

        import io.micronaut.jsonschema.*;
        import java.util.*;

        @JsonSchema(title = "GreenSalamander", uri = "/salamander/green-salamander")
        public record Salamander(
        ) {
        }
""", config)

        expect:
        schema.title == "GreenSalamander"
        schema.$id == "https://example.com/schemas/salamander/green-salamander.schema.json"
        schema.$schema != null
    }

    void "simple class schema"() {
        given:
        def schema = buildJsonSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        @JsonSchema
        public class Salamander {
            private String name;
            private int age;
            private Color color;
            private List<String> environments;
            private Map<String, List<String>> complexMap;

            public Salamander(String name, int age, Color color, List<String> environments, Map<String, List<String>> complexMap) {
            }

            public String getName() {
                return name;
            }

            public int getAge() {
                return age;
            }

            public Color getColor() {
                return color;
            }

            public List<String> getEnvironments() {
                return environments;
            }

            public Map<String, List<String>> getComplexMap() {
                return complexMap;
            }
        }

        enum Color {
            RED,
            GREEN,
            BLUE
        }
""")

        expect:
        schema.title == "Salamander"
        schema.properties['name'].type == [Schema.Type.STRING]
        schema.properties['age'].type == [Schema.Type.INTEGER]
        schema.properties['color'].type == [Schema.Type.STRING]
        schema.properties['color'].enumValues == ["RED", "GREEN", "BLUE"]
        schema.properties['environments'].type == [Schema.Type.ARRAY]
        schema.properties['environments'].items.type == [Schema.Type.STRING]
        schema.properties['complexMap'].type == [Schema.Type.OBJECT]
        schema.properties['complexMap'].additionalProperties.type == [Schema.Type.ARRAY]
        schema.properties['complexMap'].additionalProperties.items.type == [Schema.Type.STRING]
    }

    void "schema with strict configuration"() {
        given:
        def config = ["strictMode": "true"]
        def schema = buildJsonSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.*;
        import java.util.*;

        @JsonSchema
        public record Salamander(
                String name,
                boolean poisonous,
                int age
        ) {
        }
""", config)

        expect:
        schema.title == "Salamander"
        schema.properties.size() == 3
        schema.additionalProperties == Schema.FALSE
        schema.required == ["name", "poisonous", "age"]
    }

    void "validation schema"() {
        given:
        def schema = buildJsonSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import jakarta.annotation.Nullable;
        import jakarta.validation.constraints.*;
        import java.util.*;
        import java.math.BigDecimal;

        @JsonSchema
        public record Salamander(
                @NotEmpty
                List<String> colors,
                @Size(min = 2, max = 10)
                List<@Size(min = 3) String> environments,
                @NotBlank
                String skinColor,
                @Size(min = 3, max = 20)
                @Pattern(regexp = "[a-zA-Z \\\\-]+")
                String species,
                @PositiveOrZero
                int age,
                @Negative
                int negative,
                @Min(10)
                @Max(100)
                long integer,
                @DecimalMin("10")
                @DecimalMax("100.5")
                double number,
                @Null
                String alwaysNull,
                @Nullable
                String nullable,
                @AssertFalse
                boolean alwaysFalse,
                @AssertTrue
                boolean alwaysTrue,
                @Digits(integer = 4, fraction = 4)
                BigDecimal digits
        ) {
        }
""")

        expect:
        schema.title == "Salamander"
        schema.properties['colors'].minItems == 1
        schema.properties['environments'].minItems == 2
        schema.properties['environments'].maxItems == 10
        schema.properties['environments'].items.minLength == 3
        schema.properties['skinColor'].minLength == 1
        schema.properties['species'].minLength == 3
        schema.properties['species'].maxLength == 20
        schema.properties['species'].pattern == '[a-zA-Z \\-]+'
        schema.properties['age'].minimum == 0
        schema.properties['negative'].exclusiveMaximum == 0
        schema.properties['integer'].minimum == 10
        schema.properties['integer'].maximum == 100
        schema.properties['number'].minimum == 10
        schema.properties['number'].maximum == 100.5
        schema.properties['alwaysNull'].type == [Schema.Type.NULL]
        schema.properties['nullable'].type == [Schema.Type.STRING]
        schema.properties['alwaysTrue'].constValue == true
        schema.properties['alwaysFalse'].constValue == false
        schema.properties['digits'].type == [Schema.Type.NUMBER]
        schema.properties['digits'].exclusiveMaximum == 10000
        schema.properties['digits'].exclusiveMinimum == -10000
        schema.properties['digits'].multipleOf == 0.0001
    }

    void "required properties schema"() {
        given:
        def schema = buildJsonSchema('test.ClownFish', 'clown-fish', """
        package test;

        import io.micronaut.core.annotation.NonNull;
        import io.micronaut.jsonschema.JsonSchema;
        import jakarta.annotation.Nonnull;
        import jakarta.validation.constraints.*;

        @JsonSchema
        public record ClownFish(
                @Nonnull
                String name,
                @NotNull
                String color,
                @NonNull
                Double weight,
                Integer age
        ) {
        }
""")

        expect:
        schema.title == "ClownFish"
        schema.required == ['name', 'color', 'weight']
    }

    void "class schema with documentation"() {
        given:
        def schema = buildJsonSchema('test.Heron', 'heron', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        /**
         * A long-legged, long-necked, freshwater and coastal bird.
         */
        @JsonSchema
        public class Heron {
            private String name;
            private int age;
            private Color color;
            private Color beakColor;

            public Heron(String name, int age, Color color, Color beakColor) {
            }

            /**
             * The name.
             */
            public String getName() {
                return name;
            }

            /**
             * The age.
             */
            public int getAge() {
                return age;
            }

            public Color getColor() {
                return color;
            }

            /**
             * The color of the beak.
             */
            public Color getBeakColor() {
                return beakColor;
            }
        }

        /**
         * The feather color.
         */
        enum Color {
            WHITE,
            BLUE,
            GREY
        }
""")

        expect:
        schema.title == "Heron"
        schema.description == "A long-legged, long-necked, freshwater and coastal bird."
        schema.properties['name'].description == "The name."
        schema.properties['age'].description == "The age."
        schema.properties['color'].description == "The feather color."
        schema.properties['beakColor'].description == "The color of the beak."
    }

    void "record schema with documentation"() {
        given:
        def schema = buildJsonSchema('test.Heron', 'heron', """
        package test;

        import io.micronaut.jsonschema.JsonSchema;
        import java.util.*;

        /**
         * A long-legged, long-necked, freshwater and coastal bird.
         *
         * @param name The name.
         * @param age The age.
         * @param beakColor The color of the beak.
         */
        @JsonSchema
        public record Heron (
                String name,
                int age,
                Color color,
                Color beakColor
        ) {
        }

        /**
         * The feather color.
         */
        enum Color {
            WHITE,
            BLUE,
            GREY
        }
""")

        expect:
        schema.title == "Heron"
        schema.description == "A long-legged, long-necked, freshwater and coastal bird."
        schema.properties['name'].description == "The name."
        schema.properties['age'].description == "The age."
        schema.properties['color'].description == "The feather color."
        schema.properties['beakColor'].description == "The color of the beak."
    }

}

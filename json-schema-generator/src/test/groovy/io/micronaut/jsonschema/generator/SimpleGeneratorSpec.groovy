package io.micronaut.jsonschema.generator

class SimpleGeneratorSpec extends AbstractGeneratorSpec {

    void testEnumGeneration() {
        when:
        var content = generateTypeAndGetContent("LlamaRecord", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/status.schema.json",
          "title":"Status",
          "type": "string",
          "enum": [
             "active",
             "in progress",
             "deleted"
          ]
        }
        ''')

        then:
        content == """
        @Serdeable
        public enum Status(
            ACTIVE("active")
            IN_PROGRESS("in-progress"),
            DELETED("deleted");

            private final String value;

            public Status(String value) {
                this.value = value;
            }

            @JsonCreator
            public Status statusOf(String value) {
                return switch (value) {
                    case "active" -> ACTIVE;
                    case "in-progress" -> IN_PROGRESS;
                    case "deleted" -> DELETED;
                };
            }

            @JsonValue
            public String getValue() {
                return value;
            }
        ) {
        }""".stripIndent().trim()
    }

    void testRecordGeneration() {
        when:
        var content = generateTypeAndGetContent("LlamaRecord", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/llama.schema.json",
          "title":"Llama",
          "description":"A llama. <4>",
          "type":["object"],
          "properties":{
            "age":{
              "description":"The age",
              "type":["integer"],
              "minimum":0
            },
            "name":{
              "description":"The name",
              "type":"string",
              "minLength":1
            },
            "hours":{
              "description":"Happy hours",
              "type":"array",
              "items": {
                "type": "number"
              }
            }
          },
          "required": ["age", "name"]
        }
        ''')

        then:
        content == """
        @Serdeable
        public record LlamaRecord(
            @NotNull @Min(0) int age,
            @NotNull @Size(min = 1) String name,
            List<Float> hours
        ) {
        }""".stripIndent().trim()
    }

    void testRecordNamingGeneration() {
        when:
        var type = generateType("MyLlamaNumberOneRecord", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/llama.schema.json",
          "title":"My Llama-Number One",
          "type":["object"],
          "properties":{
            "name": { "type": "string" }
          }
        }
        ''')

        then:
        type != null
        type.name.asString() == "MyLlamaNumberOneRecord"
    }

    void testPropertyGeneration() {
        when:
        var content = generatePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName          | propertySchema                                                        | expectedJava
        // TODO support all string formats: https://json-schema.org/understanding-json-schema/reference/string
        'string'              | '{"type": "string"}'                                                  | 'String string'
        'date'                | '{"type": "string", "format": "date"}'                                | 'LocalDate date'
        'date'                | '{"type": "string", "format": "date-time"}'                           | 'ZonedDateTime date'
        // https://json-schema.org/understanding-json-schema/reference/numeric
        'integer'             | '{"type": "integer"}'                                                 | 'int integer'
        'test'                | '{"type": "number"}'                                                  | "float test"
        // https://json-schema.org/understanding-json-schema/reference/array
        'array'               | '{"type": "array", "items": {"type": "string"}}'                      | "List<String> array"
        'array'               | '{"type": "array", "uniqueItems": true, "items": {"type": "string"}}' | "Set<String> array"
        'array'               | '{"type": "array", "items": {"type": "number"}}'                      | "List<Float> array"
        // TODO booleans
        // TODO enums
        // TODO support unusual names
        'my unusual property' | '{"type": "string"}'                                                  | '@JsonProperty("my unusual property") String myUnusualProperty'
    }

    void testPropertyValidationGeneration() {
        when:
        var content = generatePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName | propertySchema                                                  | expectedJava
        // TODO fill in more test cases
        'test'       | '{"type": "number", "minimum": 10}'                             | "@DecimalMin(10) float test"
        'array'      | '{"type": "array", "items": {"type": "number", "minimum": 10}}' | "List<@DecimalMin(10) Float> array"
    }

}

package io.micronaut.jsonschema.generator

class SimpleGeneratorSpec extends AbstractGeneratorSpec {

    void testEnumGeneration() {
        when:
        var content = generateTypeAndGetContent("Status", '''
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
        public enum Status {

          ACTIVE("active"),
          IN_PROGRESS("in progress"),
          DELETED("deleted");

          public String name;

          private Status(String name) {
            this.name = name;
          }

          @JsonValue
          public String getName() {
            return this.name;
          }

          @JsonCreator
          public static Status statusOf(String name) {
            return switch (name) {
                  case "active" -> ACTIVE;
                  case "in progress" -> IN_PROGRESS;
                  case "deleted" -> DELETED;
                  default -> null;
                };
          }
        }""".stripIndent().trim()
    }

    void testRecordGeneration() {
        when:
        var content = generateTypeAndGetContent("Llama", '''
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
                "type": "number",
                "minimum": 0.0
              }
            }
          },
          "required": ["age", "name"]
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Llama(
            @NotNull @Min(0) int age,
            @NotNull @Size(min = 1) String name,
            List<@DecimalMin(\"0.0\") Float> hours
        ) {
        }""".stripIndent().trim()
    }

    void testAdditionalProperties() {
        when:
        var content = generateTypeAndGetContent("Llama2", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/llama.schema.json",
          "title":"Llama2",
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
                "type": "number",
                "minimum": 0.0
              }
            }
          },
          "required": ["age", "name"],
          "additionalProperties": {"type": "string"}
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Llama2(
            @NotNull @Min(0) int age,
            @NotNull @Size(min = 1) String name,
            List<@DecimalMin(\"0.0\") Float> hours,
            HashMap<String, String> unknownFields
        ) {
          @JsonAnyGetter
          public HashMap<String, String> otherFields() {
            return unknownFields;
          }

          @JsonAnySetter
          public void setOtherField(String name, String value) {
            if (unknownFields == null) {
              unknownFields = new java.util.HashMap();
            }
            unknownFields.put(name, value);
          }
        }""".stripIndent().trim()
    }

    void testRecordNamingGeneration() {
        when:
        var type = generateType("MyLlamaNumberOne", '''
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
        type.name.asString() == "MyLlamaNumberOne"
    }

    void testPropertyGeneration() {
        when:
        var content = generatePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName          | propertySchema                                                        | expectedJava
        // support all string formats: https://json-schema.org/understanding-json-schema/reference/string
        'string'              | '{"type": "string"}'                                                  | 'String string'
        'date'                | '{"type": "string", "format": "date"}'                                | 'LocalDate date'
        'date'                | '{"type": "string", "format": "date-time"}'                           | 'ZonedDateTime date'
        'time'                | '{"type": "string", "format": "time"}'                                | 'ZonedDateTime time'
        'duration'            | '{"type": "string", "format": "duration"}'                            | 'Duration duration'
        'ip'                  | '{"type": "string", "format": "ipv4"}'                                | 'Inet4Address ip'
        'ip'                  | '{"type": "string", "format": "ipv6"}'                                | 'Inet6Address ip'
        'uuid'                | '{"type": "string", "format": "uuid"}'                                | 'UUID uuid'
        'uri'                 | '{"type": "string", "format": "uri"}'                                 | 'URI uri'
        'iri'                 | '{"type": "string", "format": "iri"}'                                 | 'URI iri'
        'pointer'             | '{"type": "string", "format": "json-pointer"}'                        | 'JsonPointer pointer'
        // https://json-schema.org/understanding-json-schema/reference/numeric
        'integer'             | '{"type": "integer"}'                                                 | 'int integer'
        'test'                | '{"type": "number"}'                                                  | "float test"
        // https://json-schema.org/understanding-json-schema/reference/array
        'array'               | '{"type": "array", "items": {"type": "string"}}'                      | "List<String> array"
        'array'               | '{"type": "array", "uniqueItems": true, "items": {"type": "string"}}' | "Set<String> array"
        'array'               | '{"type": "array", "items": {"type": "number"}}'                      | "List<Float> array"
        // booleans
        'predicate'           | '{"type": "boolean"}'                                                 | 'boolean predicate'
        // enums
        'status'              | '{"type": "string", "enum": ["SINGLE", "TAKEN"]}'                     | 'Status status'
        // support unusual names
        'isTrue'              | '{"type": "boolean"}'                                                 | 'boolean isTrue'
        'short'               | '{"type": "number"}'                                                  | '@JsonProperty("short") float short_json'
        '#bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("#bikes") int bikes'
        '9bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("9bikes") int bikes'
        'bikes9times'         | '{"type": "integer"}'                                                 | 'int bikes9times'
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
        'test'       | '{"type": "number", "minimum": 10}'                             | "@DecimalMin(\"10\") float test"
        'array'      |'{"type": "array", "items": {"type": "number", "minimum": 10.0}}'| "List<@DecimalMin(\"10.0\") Float> array"
        'arrayMulti' |'{"type": "array", "items": {"type": "array", ' +
                '"items": {"type": "number", "minimum": 10.0}, ' +
                '"uniqueItems": true, "minItems": 2}, "minItems": 1}'                  | "@Size(min = 1) List<@Size(min = 2) Set<@DecimalMin(\"10.0\") Float>> arrayMulti"
    }

}

package io.micronaut.jsonschema.generator


import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder

import java.nio.file.Path

class SimpleGeneratorSpec extends AbstractGeneratorSpec {

    void testArrayGeneration() {
        when:
        SourceGenerator generator = new SourceGenerator("java")

        Path outputPath = new File("output").toPath() // Define the base output path
        String packageName = "com.example.project"; // Example package name
        String fileName = "ArrayObject";
        var jsonSchema = '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/status.schema.json",
          "title":"ArrayObject",
          "type": "array",
          "items": {
            "$ref": "string"
          }
        }
        ''';
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
                .withInputStream(new ByteArrayInputStream(jsonSchema.getBytes()))
                .withOutputFolder(outputPath)
                .withOutputPackageName(packageName)
                .withOutputFileName(fileName)
                .build())

        then:
        generated == null
    }

    void testRecordGeneration() {
        when:
        var content = generateTypeAndGetContent("Llama", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/llama.schema.json",
          "title":"Llama",
          "description":"A llama.",
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

    void testRecordGenerationWithRecursion() {
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
              "description":"This",
              "$ref": "#"
            },
            "hours":{
              "description":"Happy hours",
              "type":"array",
              "items": {
                "$ref": "#"
              }
            }
          }
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Llama(
            @Min(0) int age,
            Llama name,
            List<Llama> hours
        ) {
        }""".stripIndent().trim()
    }

    void testRecordGenerationWithInnerRecord() {
        when:
        var content = generateTypeAndGetContent("Default", '''
        {
          "title":"Default",
          "description":"A record with inner record.",
          "type":"object",
          "properties":{
            "age":{
              "description":"The age",
              "type":["integer"],
              "minimum":0
            },
            "defaults": {
              "type": "object",
              "properties": {
                "run": {
                  "type": "object",
                  "properties": {
                    "shell": {
                      "type": "string",
                      "enum": ["bash", "pwsh", "python", "sh", "cmd", "powershell"]
                    },
                    "working-directory": {
                      "type": "string",
                      "pattern": "^[a-zA-Z]*"
                    }
                  },
                  "additionalProperties": false
                }
              },
              "additionalProperties": false
            }
          }
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Default(
            @Min(0) int age,
            Defaults defaults
        ) {
          @Serdeable
          public record Defaults(
              Run run
          ) {
            @Serdeable
            public record Run(
                Shell shell,
                @JsonProperty("working-directory") @Pattern(regexp = "^[a-zA-Z]*") String workingDirectory
            ) {
              @Serdeable
              public enum Shell {

                BASH("bash"),
                PWSH("pwsh"),
                PYTHON("python"),
                SH("sh"),
                CMD("cmd"),
                POWERSHELL("powershell");

                public String value;

                private Shell(String value) {
                  this.value = value;
                }

                @JsonValue
                public String getValue() {
                  return this.value;
                }

                @JsonCreator
                public static Run.Shell statusOf(String value) {
                  return switch (value) {
                    case "bash" -> BASH;
                    case "pwsh" -> PWSH;
                    case "python" -> PYTHON;
                    case "sh" -> SH;
                    case "cmd" -> CMD;
                    case "powershell" -> POWERSHELL;
                    default -> null;
                  };
                }
              }
            }
          }
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
        public class Llama2 {
          /**
           * The age
           */
          private @NotNull @Min(0) int age;

          /**
           * The name
           */
          private @NotNull @Size(min = 1) String name;

          /**
           * Happy hours
           */
          private List<@DecimalMin("0.0") Float> hours;

          HashMap<String, String> unknownFields;

          public @NotNull @Min(0) int getAge() {
            return this.age;
          }

          public void setAge(@NotNull @Min(0) int age) {
            this.age = age;
          }

          public @NotNull @Size(min = 1) String getName() {
            return this.name;
          }

          public void setName(@NotNull @Size(min = 1) String name) {
            this.name = name;
          }

          public List<@DecimalMin("0.0") Float> getHours() {
            return this.hours;
          }

          public void setHours(List<@DecimalMin("0.0") Float> hours) {
            this.hours = hours;
          }

          @JsonAnyGetter
          public HashMap<String, String> getUnknownFields() {
            return this.unknownFields;
          }

          @JsonAnySetter
          public void setUnknownFields(String name, String value) {
            if (this.unknownFields == null) {
              this.unknownFields = new java.util.HashMap();
            }
            this.unknownFields.put(name, value);
          }
        }""".stripIndent().trim()
    }

    void testMapGeneration() {
        when:
        var content = generateTypeAndGetContent(null, '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/hedgehog.schema.json",
          "title":"Hedgehog",
          "type": "object",
          "properties":{
            "spikes": {
              "type": "object",
              "additionalProperties": {
                "$ref": "#/$defs/Spike"
              }
            },
            "aliases":{
              "type": "object",
              "additionalProperties": {
                "type": "string"
              }
            },
            "properties": {
              "type": "object",
              "additionalProperties": true
            }
          },
          "$defs": {
            "Spike": {
              "type": "object",
              "properties": {
                "length": {
                  "type": "number"
                }
              }
            }
          }
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Hedgehog(
            Map<String, Spike> spikes,
            Map<String, String> aliases,
            Map<String, Object> properties
        ) {
        }""".stripIndent().trim()
    }

    void testAllOf() {
        when:
        var content = generateTypeAndGetContent("Llama3", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/llama.schema.json",
          "title":"Llama3",
          "description":"A llama. <4>",
          "type":["object"],
          "properties":{
            "name":{
              "description":"The name",
              "type":"string",
              "minLength":1
            }
          },
          "allOf": [
            {
              "properties": {
                "foo": { "type": "string" }
              },
              "required": [ "foo" ]
            },
            {
              "properties": {
                "bar": {
                  "type": "number",
                  "allOf": [
                    { "minimum": 18 }
                  ]
                }
              },
              "required": [ "bar" ]
            }
          ],
          "required": ["name"]
        }
        ''')

        then:
        content == """
        @Serdeable
        public record Llama3(
            @NotNull @Size(min = 1) String name,
            @NotNull String foo,
            @NotNull @DecimalMin("18") float bar
        ) {
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
        ''', (b) -> {})

        then:
        type != null
        type.name.asString() == "MyLlamaNumberOne"
    }

    void testJavadocGeneration() {
        when:
        var type = generateType("Elephant", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/elephant.schema.json",
          "title": "Elephant",
          "description":"A elephant <(|)>.\\nAnother line",
          "type":["object"],
          "properties":{
            "name": {
              "type": "string"
            }
          }
        }
        ''', b -> {})

        then:
        type.getJavadoc().get().toText() == """A elephant &lt;(|)&gt;.<br>\nAnother line\n"""
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
        'test'                | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*$"}'             | "int test"
        'test'                | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*.?[0-9]+$"}'     | "float test"
        // https://json-schema.org/understanding-json-schema/reference/array
        'array'               | '{"type": "array", "items": {"type": "string"}}'                      | "List<String> array"
        'array'               | '{"type": "array", "uniqueItems": true, "items": {"type": "string"}}' | "Set<String> array"
        'array'               | '{"type": "array", "items": {"type": "number"}}'                      | "List<Float> array"
        // support contains
        'contain'             | '{"type": "array", "contains": {"type": "number"}}'                   | "List<Float> contain"
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
        propertyName | propertySchema                                                    | expectedJava
        'test'       | '{"type": "number", "minimum": 10}'                               | "@DecimalMin(\"10\") float test"
        'test'       | '{"type": "number", "maximum": 10}'                               | "@DecimalMax(\"10\") float test"
        'test'       | '{"type": "number", "exclusiveMaximum": 10.0}'                    | "@DecimalMax(\"9.999\") float test"
        'test'       | '{"type": "number", "exclusiveMinimum": 10.0}'                    | "@DecimalMin(\"10.001\") float test"
        'test'       | '{"type": "number", "pattern": "^[1-9][0-9]*$"}'                  | "@Min(1) int test"
        'test'       | '{"type": "number", "pattern": "^[1-9][0-9]*.?[0-9]+$"}'          | "@DecimalMin(\"0.001\") float test"
        'test'       | '{"type": "number", "pattern": "^[0]|([1-9][0-9]*)$"}'            | "@Min(0) int test"
        'test'       | '{"type": "number", "pattern": "^-d+$"}'                          | "@Max(0) int test"
        'test'       | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*$"}'         | "int test"
        'test'       | '{"type": "string", "pattern": "[A-Z]+"}'                         | "@Pattern(regexp = \"[A-Z]+\") String test"
        'test'       | '{"type": "boolean", "const": true}'                              | "@AssertTrue boolean test"
        'test'       | '{"type": "boolean", "const": false}'                             | "@AssertFalse boolean test"
        // nullable
        'test'       | '{"type": ["boolean", "null"]}'                                   | "Boolean test"
        'test'       | '{"type": ["integer", "null"]}'                                   | "Integer test"
        'test'       | '{"type": ["number", "null"]}'                                    | "Float test"
        'test'       | '{"type": ["object", "null"]}'                                    | "Object test"
        // array annotations
        'array'      | '{"type": "array", "items": {"type": "number", "minimum": 10.0}}' | "List<@DecimalMin(\"10.0\") Float> array"
        'arrayMulti' | '{"type": "array", "items": {"type": "array", ' +
                '"items": {"type": "number", "minimum": 10.0}, ' +
                '"uniqueItems": true, "minItems": 2}, "minItems": 1}'                    | "@Size(min = 1) List<@Size(min = 2) Set<@DecimalMin(\"10.0\") Float>> arrayMulti"
        'array'      | '{"type": "array", "contains": {"type": "number"}, ' +
                '"minContains": 2, "maxContains": 3}'                                    | "@Size(max = 3) @Size(min = 2) List<Float> array"
        'array'      | '{"type": "array", "items": {"type": "number"}, ' +
                '"minLength": 2, "maxLength": 3}'                                        | "@Size(max = 3) @Size(min = 2) List<Float> array"
        'array'      | '{"type": "array", "items": {"type":"number"},"nullable": true}'  | "@Nullable List<Float> array"
        'array'      | '{"type": "array", "items": {"type":"number"},"nullable": false}' | "@NotNull List<Float> array"
    }

}

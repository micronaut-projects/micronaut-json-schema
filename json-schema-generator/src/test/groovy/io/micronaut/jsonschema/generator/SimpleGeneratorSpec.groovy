package io.micronaut.jsonschema.generator


import io.micronaut.jsonschema.generator.loaders.FileProcessor
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder

import java.nio.file.Files
import java.nio.file.Path

class SimpleGeneratorSpec extends AbstractGeneratorSpec {

    void testArrayGeneration() {
        when:
        SourceGenerator generator = new SourceGenerator("java")

        Path outputPath = Files.createTempDirectory("json-schema-generator-output")
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
            "counts":{
              "type": "object",
              "additionalProperties": {
                "type": "integer"
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
            Map<String, Integer> counts,
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

    void "allOf local ref branch flattens into generated object"() {
        when:
        var content = generatePreparedCompositionTypeAndGetContent("Composed", '''
        {
          "title":"Composed",
          "allOf": [
            { "$ref": "#/$defs/Base" },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              },
              "required": ["name"]
            }
          ],
          "$defs": {
            "Base": {
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              },
              "required": ["id"]
            }
          }
        }
        ''')

        then:
        content.contains("@NotNull int id")
        content.contains("@NotNull String name")
    }

    void "allOf definitions ref branch flattens into generated object"() {
        when:
        var content = generatePreparedCompositionTypeAndGetContent("Composed", '''
        {
          "title":"Composed",
          "allOf": [
            { "$ref": "#/definitions/Base" },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          ],
          "definitions": {
            "Base": {
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              }
            }
          }
        }
        ''')

        then:
        content.contains("int id")
        content.contains("String name")
    }

    void "compatible allOf duplicate property constraints merge only during composition preparation"() {
        when:
        var content = generatePreparedCompositionTypeAndGetContent("Constrained", '''
        {
          "title":"Constrained",
          "allOf": [
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "minLength": 2 }
              },
              "required": ["name"]
            },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "maxLength": 8 }
              }
            }
          ]
        }
        ''')

        then:
        content.contains("@NotNull")
        content.contains("@Size(min = 2)")
        content.contains("@Size(max = 8)")
        content.contains("String name")
    }

    void "root local ref generates requested top-level object"() {
        when:
        var content = generatePreparedCompositionTypeAndGetContent("Alias", '''
        {
          "$ref": "#/$defs/Base",
          "$defs": {
            "Base": {
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              },
              "required": ["id"]
            }
          }
        }
        ''')

        then:
        content.contains("public record Alias")
        content.contains("@NotNull int id")
    }

    void "property definitions ref resolves to generated definition type"() {
        given:
        SourceGenerator generator = new SourceGenerator("java")
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        def config = new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
        {
          "title":"DefinitionReference",
          "type":"object",
          "properties": {
            "base": { "$ref": "#/definitions/Base" }
          },
          "definitions": {
            "Base": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              },
              "additionalProperties": false
            }
          },
          "additionalProperties": false
        }
        '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("DefinitionReference")
            .build()
        def schema = FileProcessor.getJsonSchema(config)
        SchemaCompositionSupport.prepareLocalCompositionReferences(schema)
        File generated = generator.generate(config, schema)

        then:
        generated.text.contains("Base base")
        Files.exists(outputPath.resolve("com/example/project/Base.java"))
    }

    void "referenced definition allOf local ref branch flattens before definition generation"() {
        given:
        SourceGenerator generator = new SourceGenerator("java")
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        def config = new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"Container",
              "type":"object",
              "properties":{
                "value": { "$ref": "#/$defs/Composed" }
              },
              "$defs": {
                "Composed": {
                  "allOf": [
                    { "$ref": "#/$defs/Base" },
                    {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" }
                      },
                      "required": ["name"]
                    }
                  ]
                },
                "Base": {
                  "type": "object",
                  "properties": {
                    "id": { "type": "integer" }
                  },
                  "required": ["id"]
                }
              }
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .build()
        def schema = FileProcessor.getJsonSchema(config)
        SchemaCompositionSupport.prepareLocalCompositionReferences(schema)
        generator.generate(config, schema)
        String definitionContent = Files.readString(outputPath.resolve("com/example/project/Composed.java"))

        then:
        definitionContent.contains("@NotNull int id")
        definitionContent.contains("@NotNull String name")
    }

    void "incompatible allOf local ref branch falls back to Object at property level"() {
        given:
        def context = new io.micronaut.jsonschema.generator.utils.GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(io.micronaut.inject.visitor.VisitorContext.Language.JAVA, context)
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"AllOfRefConflict",
              "type":"object",
              "properties":{
                "value": {
                  "allOf": [
                    { "$ref": "#/$defs/StringValue" },
                    {
                      "type": "object",
                      "properties": {
                        "code": { "type": "integer" }
                      }
                    }
                  ]
                }
              },
              "$defs": {
                "StringValue": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "string" }
                  }
                }
              },
              "additionalProperties": false
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("AllOfRefConflict")
            .build())

        then:
        generated.text.contains("Object value")
        generator.warnings*.code() == ["UNSUPPORTED_KEYWORD"]
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
        var content = generateRecordProfilePropertyAndGetContent(propertyName, propertySchema)

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
        'integer'             | '{"type": "integer"}'                                                 | 'Integer integer'
        'test'                | '{"type": "number"}'                                                  | "Float test"
        'test'                | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*$"}'             | "Integer test"
        'test'                | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*.?[0-9]+$"}'     | "Float test"
        // https://json-schema.org/understanding-json-schema/reference/array
        'array'               | '{"type": "array", "items": {"type": "string"}}'                      | "List<String> array"
        'array'               | '{"type": "array", "uniqueItems": true, "items": {"type": "string"}}' | "Set<String> array"
        'array'               | '{"type": "array", "items": {"type": "number"}}'                      | "List<Float> array"
        // support contains
        'contain'             | '{"type": "array", "contains": {"type": "number"}}'                   | "List<Float> contain"
        // booleans
        'predicate'           | '{"type": "boolean"}'                                                 | 'Boolean predicate'
        // enums
        'status'              | '{"type": "string", "enum": ["SINGLE", "TAKEN"]}'                     | 'Status status'
        // support unusual names
        'isTrue'              | '{"type": "boolean"}'                                                 | 'Boolean isTrue'
        'short'               | '{"type": "number"}'                                                  | '@JsonProperty("short") Float short_json'
        '#bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("#bikes") Integer bikes'
        '9bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("9bikes") Integer bikes'
        'bikes9times'         | '{"type": "integer"}'                                                 | 'Integer bikes9times'
        'my unusual property' | '{"type": "string"}'                                                  | '@JsonProperty("my unusual property") String myUnusualProperty'
    }

    void "default property generation keeps existing scalar primitive behavior"() {
        when:
        var content = generatePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName  | propertySchema                                                    | expectedJava
        'integer'     | '{"type": "integer"}'                                             | 'int integer'
        'test'        | '{"type": "number"}'                                              | 'float test'
        'test'        | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*$"}'         | 'int test'
        'test'        | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*.?[0-9]+$"}' | 'float test'
        'predicate'   | '{"type": "boolean"}'                                             | 'boolean predicate'
        'isTrue'      | '{"type": "boolean"}'                                             | 'boolean isTrue'
        'short'       | '{"type": "number"}'                                              | '@JsonProperty("short") float short_json'
        '#bikes'      | '{"type": ["integer"]}'                                           | '@JsonProperty("#bikes") int bikes'
        '9bikes'      | '{"type": ["integer"]}'                                           | '@JsonProperty("9bikes") int bikes'
        'bikes9times' | '{"type": "integer"}'                                             | 'int bikes9times'
    }

    void testPropertyValidationGeneration() {
        when:
        var content = generateRecordProfilePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName | propertySchema                                                    | expectedJava
        'test'       | '{"type": "number", "minimum": 10}'                               | "@DecimalMin(\"10\") Float test"
        'test'       | '{"type": "number", "maximum": 10}'                               | "@DecimalMax(\"10\") Float test"
        'test'       | '{"type": "number", "exclusiveMaximum": 10.0}'                    | "@DecimalMax(\"9.999\") Float test"
        'test'       | '{"type": "number", "exclusiveMinimum": 10.0}'                    | "@DecimalMin(\"10.001\") Float test"
        'test'       | '{"type": "number", "pattern": "^[1-9][0-9]*$"}'                  | "@Min(1) Integer test"
        'test'       | '{"type": "number", "pattern": "^[1-9][0-9]*.?[0-9]+$"}'          | "@DecimalMin(\"0.001\") Float test"
        'test'       | '{"type": "number", "pattern": "^[0]|([1-9][0-9]*)$"}'            | "@Min(0) Integer test"
        'test'       | '{"type": "number", "pattern": "^-d+$"}'                          | "@Max(0) Integer test"
        'test'       | '{"type": "number", "pattern": "^[0]|[-+]?[1-9][0-9]*$"}'         | "Integer test"
        'test'       | '{"type": "string", "pattern": "[A-Z]+"}'                         | "@Pattern(regexp = \"[A-Z]+\") String test"
        'test'       | '{"type": "boolean", "const": true}'                              | "Boolean test"
        'test'       | '{"type": "boolean", "const": false}'                             | "Boolean test"
        // nullable
        'test'       | '{"type": ["boolean", "null"]}'                                   | "@Nullable Boolean test"
        'test'       | '{"type": ["integer", "null"]}'                                   | "@Nullable Integer test"
        'test'       | '{"type": ["number", "null"]}'                                    | "@Nullable Float test"
        'test'       | '{"type": ["object", "null"]}'                                    | "@Nullable Object test"
        'test'       | '{"oneOf": [{"type": "null"}, {"type": "string", "maxLength": 20}]}' | "@Nullable @Size(max = 20) String test"
        'test'       | '{"anyOf": [{"type": "null"}, {"type": "string", "maxLength": 20}]}' | "@Nullable @Size(max = 20) String test"
        // array annotations
        'array'      | '{"type": "array", "items": {"type": "number", "minimum": 10.0}}' | "List<@DecimalMin(\"10.0\") Float> array"
        'arrayMulti' | '{"type": "array", "items": {"type": "array", ' +
                '"items": {"type": "number", "minimum": 10.0}, ' +
                '"uniqueItems": true, "minItems": 2}, "minItems": 1}'                    | "@Size(min = 1) List<@Size(min = 2) Set<@DecimalMin(\"10.0\") Float>> arrayMulti"
        'array'      | '{"type": "array", "contains": {"type": "number"}, ' +
                '"minContains": 2, "maxContains": 3}'                                    | "@Size(max = 3) @Size(min = 2) List<Float> array"
        'array'      | '{"type": "array", "items": {"type": "number"}, ' +
                '"minLength": 2, "maxLength": 3}'                                        | "@Size(max = 3) @Size(min = 2) List<Float> array"
        'array'      | '{"type": "array", "items": {"type":["string", "null"]}}'          | "List<@Nullable String> array"
        'array'      | '{"type": "array", "items": {"type":"number"},"nullable": true}'  | "@Nullable List<Float> array"
        'array'      | '{"type": "array", "items": {"type":"number"},"nullable": false}' | "@NotNull List<Float> array"
    }

    void "default property validation generation keeps existing const and nullable behavior"() {
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
        'test'       | '{"type": "boolean", "const": true}'                              | "@AssertTrue boolean test"
        'test'       | '{"type": "boolean", "const": false}'                             | "@AssertFalse boolean test"
        'test'       | '{"type": ["boolean", "null"]}'                                   | "Boolean test"
        'test'       | '{"type": ["integer", "null"]}'                                   | "Integer test"
        'test'       | '{"type": ["number", "null"]}'                                    | "Float test"
        'test'       | '{"type": ["object", "null"]}'                                    | "Object test"
        'test'       | '{"oneOf": [{"type": "null"}, {"type": "string", "maxLength": 20}]}' | "Object test"
        'test'       | '{"anyOf": [{"type": "null"}, {"type": "string", "maxLength": 20}]}' | "Object test"
    }

    void "array without items maps to List of Object"() {
        expect:
        generateRecordProfilePropertyAndGetContent("array", '{"type": "array"}') == "List<Object> array"
    }

    void "unsupported array item schema maps element to Object and records warning"() {
        given:
        def context = new io.micronaut.jsonschema.generator.utils.GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(io.micronaut.inject.visitor.VisitorContext.Language.JAVA, context)
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"UnsupportedArrayItems",
              "type":"object",
              "properties":{
                "values":{
                  "type":"array",
                  "items":{"oneOf":[{"type":"string"},{"type":"integer"}]}
                }
              },
              "additionalProperties": false
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("UnsupportedArrayItems")
            .build())

        then:
        generated.text.contains("List<Object> values")
        generator.warnings*.code() == ["UNSUPPORTED_KEYWORD"]
    }

    void "multi type non null union maps to Object and records warning in record profile"() {
        given:
        def context = new io.micronaut.jsonschema.generator.utils.GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(io.micronaut.inject.visitor.VisitorContext.Language.JAVA, context)
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"UnsupportedUnion",
              "type":"object",
              "properties":{
                "value":{"type":["string","integer"]}
              },
              "additionalProperties": false
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("UnsupportedUnion")
            .build())

        then:
        generated.text.contains("Object value")
        generator.warnings*.code() == ["UNSUPPORTED_KEYWORD"]
    }

    void "required nullable property keeps nullable value semantics"() {
        when:
        var content = generateRecordProfileTypeAndGetContent("RequiredNullable", '''
        {
          "title": "RequiredNullable",
          "type": "object",
          "properties": {
            "count": { "type": ["integer", "null"] }
          },
          "required": ["count"],
          "additionalProperties": false
        }
        ''')

        then:
        content.contains("@Nullable Integer count")
        !content.contains("@NotNull @Nullable Integer count")
    }

    void "unsupported property-level composition falls back to Object and records warnings"() {
        given:
        def context = new io.micronaut.jsonschema.generator.utils.GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(io.micronaut.inject.visitor.VisitorContext.Language.JAVA, context)
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"UnsupportedProperties",
              "type":"object",
              "properties":{
                "choice":{"oneOf":[{"type":"string"},{"type":"integer"}]},
                "maybe":{"anyOf":[{"type":"string"},{"type":"integer"}]},
                "multi":{"type":["string","integer"]},
                "external":{"$ref":"https://example.com/schemas/External.schema.json"},
                "ambiguous":{"allOf":[{"type":"string"},{"type":"integer"}]}
              },
              "additionalProperties": false
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("UnsupportedProperties")
            .build())

        then:
        generated.text.contains("Object choice")
        generated.text.contains("Object maybe")
        generated.text.contains("Object multi")
        generated.text.contains("Object external")
        generated.text.contains("Object ambiguous")
        generator.warnings*.code() == ["UNSUPPORTED_KEYWORD", "UNSUPPORTED_KEYWORD", "UNSUPPORTED_KEYWORD", "UNSUPPORTED_KEYWORD", "UNSUPPORTED_KEYWORD"]
    }

    void "unsupported local ref outside defs falls back to Object and records warning at property level"() {
        given:
        def context = new io.micronaut.jsonschema.generator.utils.GeneratorContext()
        context.enableJsonSchemaRecordsProfile()
        SourceGenerator generator = new SourceGenerator(io.micronaut.inject.visitor.VisitorContext.Language.JAVA, context)
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
            .withInputStream(new ByteArrayInputStream('''
            {
              "title":"UnsupportedLocalRef",
              "type":"object",
              "properties":{
                "value": { "$ref": "#/properties/template" },
                "template": { "type": "string" }
              },
              "additionalProperties": false
            }
            '''.bytes))
            .withOutputFolder(outputPath)
            .withOutputPackageName("com.example.project")
            .withOutputFileName("UnsupportedLocalRef")
            .build())

        then:
        generated.text.contains("Object value")
        generator.warnings*.code() == ["UNSUPPORTED_KEYWORD"]
    }

    void "open object unknownFields member collision fails generation"() {
        when:
        generateRecordProfileType("OpenCollision", '''
        {
          "title":"OpenCollision",
          "type":"object",
          "properties":{
            "unknownFields":{"type":"string"}
          },
          "additionalProperties": true
        }
        ''')

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("NAME_COLLISION")
        exception.message.contains("unknownFields")
    }

    void "sanitized Java member name collision fails generation"() {
        when:
        generateRecordProfileType("SanitizedCollision", '''
        {
          "title":"SanitizedCollision",
          "type":"object",
          "properties":{
            "#bikes":{"type":"integer"},
            "9bikes":{"type":"integer"}
          },
          "additionalProperties": false
        }
        ''')

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("NAME_COLLISION")
        exception.message.contains("#bikes")
        exception.message.contains("9bikes")
        exception.message.contains("bikes")
    }

    void "record profile generates unknownFields for explicit open object without declared properties"() {
        when:
        var content = generateRecordProfileTypeAndGetContent("OpenObject", '''
        {
          "title":"OpenObject",
          "type":"object",
          "additionalProperties": true
        }
        ''')

        then:
        content.contains("HashMap<String, Object> unknownFields")
        content.contains("@JsonAnyGetter")
        content.contains("@JsonAnySetter")
    }

    void "record profile boxes primitive schema valued additional properties in unknownFields"() {
        when:
        var content = generateRecordProfileTypeAndGetContent("OpenObject", '''
        {
          "title":"OpenObject",
          "type":"object",
          "properties":{
            "name":{"type":"string"}
          },
          "additionalProperties": {
            "type": "integer"
          }
        }
        ''')

        then:
        content.contains("HashMap<String, Integer> unknownFields")
        !content.contains("HashMap<String, int> unknownFields")
    }

    void "default generator keeps existing open object without declared properties behavior"() {
        when:
        var content = generateTypeAndGetContent("OpenObject", '''
        {
          "title":"OpenObject",
          "type":"object",
          "additionalProperties": true
        }
        ''')

        then:
        !content.contains("unknownFields")
        !content.contains("@JsonAnyGetter")
        !content.contains("@JsonAnySetter")
    }

}

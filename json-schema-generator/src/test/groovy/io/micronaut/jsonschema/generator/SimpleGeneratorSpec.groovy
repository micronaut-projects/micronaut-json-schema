package io.micronaut.jsonschema.generator


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
            @Min(0) Integer age,
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
            @Min(0) Integer age,
            Default_Defaults defaults
        ) {
          @Serdeable
          public record Default_Defaults(
              Default_Defaults_Run run
          ) {
            @Serdeable
            public record Default_Defaults_Run(
                Default_Defaults_Run_Shell shell,
                @JsonProperty("working-directory") @Pattern(regexp = "^[a-zA-Z]*") String workingDirectory
            ) {
              @Serdeable
              public enum Default_Defaults_Run_Shell {

                BASH("bash"),
                PWSH("pwsh"),
                PYTHON("python"),
                SH("sh"),
                CMD("cmd"),
                POWERSHELL("powershell");

                public String value;

                private Default_Defaults_Run_Shell(String value) {
                  this.value = value;
                }

                @JsonValue
                public String getValue() {
                  return this.value;
                }

                @JsonCreator
                public static Default_Defaults_Run.Default_Defaults_Run_Shell statusOf(String value) {
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

    void "oracle profile boxes primitive additionalProperties map value types"() {
        when:
        var content = generateTypeAndGetContent("OpenCounts", '''
        {
          "title":"OpenCounts",
          "type":"object",
          "additionalProperties": {
            "type": "integer"
          }
        }
        ''', b -> b.withTreatAdditionalPropertiesAsField(true))

        then:
        content.contains("Map<String, Integer> additionalProperties")
    }

    void "oracle profile maps additionalProperties schema object to typed nested value"() {
        when:
        var content = generateTypeAndGetContent("OpenValues", '''
        {
          "title":"OpenValues",
          "type":"object",
          "additionalProperties": {
            "type": "object",
            "properties": {
              "label": { "type": "string" }
            },
            "required": ["label"],
            "additionalProperties": false
          }
        }
        ''', b -> b
            .withAddGeneratedJsonSchemaAnnotation(true)
            .withTreatAdditionalPropertiesAsField(true)
            .withSortPropertiesByName(true))

        then:
        content.contains("Map<String, OpenValues_AdditionalProperties> additionalProperties")
        content.contains("public record OpenValues_AdditionalProperties(")
        content.contains("@NotNull String label")
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
        var content = generateTypeAndGetContent("Composed", '''
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
        var content = generateTypeAndGetContent("Composed", '''
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
        content.contains("Integer id")
        content.contains("String name")
    }

    void "root local ref generates requested top-level object"() {
        when:
        var content = generateTypeAndGetContent("Alias", '''
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
        File generated = generator.generate(new SourceGeneratorConfigBuilder()
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
            .build())

        then:
        generated.text.contains("Base base")
        Files.exists(outputPath.resolve("com/example/project/Base.java"))
    }

    void "referenced definition allOf local ref branch flattens before definition generation"() {
        given:
        SourceGenerator generator = new SourceGenerator("java")
        Path outputPath = Files.createTempDirectory("json-schema-generator-output")

        when:
        generator.generate(new SourceGeneratorConfigBuilder()
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
            .build())
        String definitionContent = Files.readString(outputPath.resolve("com/example/project/Composed.java"))

        then:
        definitionContent.contains("@NotNull int id")
        definitionContent.contains("@NotNull String name")
    }

    void "incompatible allOf local ref branch falls back to Object at property level"() {
        given:
        SourceGenerator generator = new SourceGenerator("java")
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
        'status'              | '{"type": "string", "enum": ["SINGLE", "TAKEN"]}'                     | 'TestRecord_Status status'
        // support unusual names
        'isTrue'              | '{"type": "boolean"}'                                                 | 'Boolean isTrue'
        'short'               | '{"type": "number"}'                                                  | '@JsonProperty("short") Float short_'
        '#bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("#bikes") Integer bikes'
        '9bikes'              | '{"type": ["integer"]}'                                               | '@JsonProperty("9bikes") Integer _9bikes'
        'bikes9times'         | '{"type": "integer"}'                                                 | 'Integer bikes9times'
        'my unusual property' | '{"type": "string"}'                                                  | '@JsonProperty("my unusual property") String myUnusualProperty'
    }

    void testPropertyValidationGeneration() {
        when:
        var content = generatePropertyAndGetContent(propertyName, propertySchema)

        then:
        content == expectedJava

        where:
        propertyName | propertySchema                                                    | expectedJava
        'test'       | '{"type": "number", "minimum": 10}'                               | "@DecimalMin(\"10\") Float test"
        'test'       | '{"type": "number", "maximum": 10}'                               | "@DecimalMax(\"10\") Float test"
        'test'       | '{"type": "number", "exclusiveMaximum": 10.0}'                    | "@DecimalMax(value = \"10.0\", inclusive = false) Float test"
        'test'       | '{"type": "number", "exclusiveMinimum": 10.0}'                    | "@DecimalMin(value = \"10.0\", inclusive = false) Float test"
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

    void "array without items maps to List of Object"() {
        expect:
        generatePropertyAndGetContent("array", '{"type": "array"}') == "List<Object> array"
    }

    void "required nullable property keeps nullable value semantics"() {
        when:
        var content = generateTypeAndGetContent("RequiredNullable", '''
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
        SourceGenerator generator = new SourceGenerator("java")
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
        SourceGenerator generator = new SourceGenerator("java")
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

    void "synthetic additionalProperties member collision fails generation"() {
        when:
        generateType("OpenCollision", '''
        {
          "title":"OpenCollision",
          "type":"object",
          "properties":{
            "additionalProperties":{"type":"string"}
          },
          "additionalProperties": true
        }
        ''', b -> b.withTreatAdditionalPropertiesAsField(true))

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("NAME_COLLISION")
        exception.message.contains("additionalProperties")
    }

}

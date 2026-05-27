package io.micronaut.jsonschema.generator

class EnumGeneratorSpec extends AbstractGeneratorSpec {

    void testEnumGeneration() {
        when:
        var content = generateTypeAndGetContent("Status", '''
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "$id":"https://example.com/schemas/status.schema.json",
          "description":"Status für mich",
          "type": "string",
          "enum": [
             "active",
             "in progress",
             "deleted",
             "not_active",
             "non-valid"
          ]
        }
        ''')

        then:
        content == """
        @Serdeable
        public enum Status {

          ACTIVE("active"),
          IN_PROGRESS("in progress"),
          DELETED("deleted"),
          NOT_ACTIVE("not_active"),
          NON_VALID("non-valid");

          public String value;

          private Status(String value) {
            this.value = value;
          }

          @JsonValue
          public String getValue() {
            return this.value;
          }

          @JsonCreator
          public static Status statusOf(String value) {
            return switch (value) {
              case "active" -> ACTIVE;
              case "in progress" -> IN_PROGRESS;
              case "deleted" -> DELETED;
              case "not_active" -> NOT_ACTIVE;
              case "non-valid" -> NON_VALID;
              default -> null;
            };
          }
        }
        """.stripIndent().trim()
    }

}

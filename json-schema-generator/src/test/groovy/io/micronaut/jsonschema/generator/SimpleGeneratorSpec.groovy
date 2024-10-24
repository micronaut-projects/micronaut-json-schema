package io.micronaut.jsonschema.generator

class SimpleGeneratorSpec extends AbstractGeneratorSpec {

    void test() {
        when:
        var content = generateRecordAndGetContent("LlamaRecord", "llama.schema.json")

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

    // TODO add json schema here and use inputstream to generate

    // TODO test all the types

    // TODO test enums

    // TODO test lists, sets, validation inside

}

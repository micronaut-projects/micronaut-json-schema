package io.micronaut.jsonschema.test

import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class ObjectsValidationSpec extends AbstractValidationSpec {

    void "valid record"() {
        when:
        var assertions = validateJsonWithSchema(new Llama("John", 12), "llama")

        then:
        assertions.size() == 0
    }

    void "invalid record"() {
        when:
        var assertions = validateJsonWithSchema(llama, "llama")

        then:
        assertions.size() == 1
        assertions[0].message == message

        where:
        llama | message
        new Llama("", 12)      | "/name: must be at least 1 characters long"
        '{"name":null}'        | "/name: null found, [string] expected"
        new Llama("John", -12) | "/age: must have a minimum value of 0"
    }

    void "valid object"() {
        when:
        var assertions = validateJsonWithSchema(
                new Salamander().setColors(["green", "red"])
                        .setEnvironments(["pond", "river"])
                        .setSkinColor("green")
                        .setSpecies("Pond Salamander")
                        .setAge(1)
                        .setNegative(-12)
                        .setInteger(15)
                        .setNumber(20.25),
                "salamander"
        )

        then:
        assertions.size() == 0
    }

    void "invalid object"() {
        when:
        var assertions = validateJsonWithSchema(salamander, "salamander")

        then:
        assertions.size() == 1
        assertions[0].message == message

        where:
        salamander                                              | message
        new Salamander().setColors([])                          | "/colors: must have at least 1 items but found 0"
        new Salamander().setEnvironments(["pond"])              | "/environments: must have at least 2 items but found 1"
        new Salamander().setSkinColor("")                       | "/skinColor: must be at least 1 characters long"
        new Salamander().setSpecies("a-very-long-species-name") | "/species: must be at most 20 characters long"
        new Salamander().setSpecies("invalidChar\$")            | "/species: does not match the regex pattern ^[a-zA-Z \\-]+\$"
        new Salamander().setAge(-12)                            | "/age: must have a minimum value of 0"
        new Salamander().setNegative(12)                        | "/negative: must have an exclusive maximum value of 0"
        new Salamander().setInteger(1)                          | "/integer: must have a minimum value of 10"
        new Salamander().setInteger(120)                        | "/integer: must have a maximum value of 100"
        new Salamander().setNumber(10)                          | "/number: must have an exclusive minimum value of 10"
        new Salamander().setNumber(100.6)                       | "/number: must have a maximum value of 100.5"
    }

    void "valid object with inheritance"() {
        when:
        var assertions = validateJsonWithSchema(bird, "bird")

        then:
        assertions.size() == 0

        where:
        bird                          | _
        new Bird.Ostrich("Bob", 10.5) | _
        new Bird.Eagle("Blob", 31.2)  | _
    }

    void "invalid object with inheritance"() {
        when:
        var assertions = validateJsonWithSchema(bird, "bird")

        then:
        assertions.size() == 3
        assertions[0].message == ": must be valid to one and only one schema, but 0 are valid"
        assertions[1].message == message1
        assertions[2].message == message2


        where:
        bird                          | message1                                               | message2
        '{"@type":"unknown-bird"}'    | "/@type: must be the constant value 'ostrich-bird'"    | "/@type: must be the constant value 'eagle-bird'"
        new Bird.Ostrich("Glob", -12) | "/runSpeed: must have an exclusive minimum value of 0" | "/@type: must be the constant value 'eagle-bird'"
        new Bird.Eagle("Blob", 0.5)   | "/@type: must be the constant value 'ostrich-bird'"    | "/flySpeed: must have a minimum value of 1"
    }

    void "valid object with reference"() {
        when:
        var possum = new Possum("Bob", [
                new Possum("Alice", [], new Possum.Environment("field"))
        ], new Possum.Environment("marshland"))
        var assertions = validateJsonWithSchema(possum, "possum")

        then:
        assertions.size() == 0
    }

    void "invalid object with references"() {
        when:
        var assertions = validateJsonWithSchema(possum, "possum")

        then:
        assertions.size() == 1
        assertions[0].message == message

        where:
        possum                                                                            | message
        new Possum("", [], new Possum.Environment("forest"))                              | "/name: must be at least 1 characters long"
        new Possum("Bob", [], new Possum.Environment("f"))                                | "/environment/name: must be at least 2 characters long"
        new Possum("Bob", [new Possum("", null, null)], null)                             | "/children/0/name: must be at least 1 characters long"
        new Possum("Bob", [new Possum("Alice", null, new Possum.Environment("f"))], null) | "/children/0/environment/name: must be at least 2 characters long"
    }

    void "valid object with changed path"() {
        when:
        var bird = new RWBlackbird("Clara", 1.2)
        var assertions = validateJsonWithSchema(bird, "red-winged-blackbird")

        then:
        assertions.size() == 0
    }

    void "invalid object with changed path"() {
        when:
        var bird = '{"name":12}'
        var assertions = validateJsonWithSchema(bird, "red-winged-blackbird")

        then:
        assertions.size() == 1
        assertions[0].message == "/name: integer found, [string] expected"
    }

}

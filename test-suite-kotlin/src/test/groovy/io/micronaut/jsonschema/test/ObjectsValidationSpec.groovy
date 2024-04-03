package io.micronaut.jsonschema.test

import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class ObjectsValidationSpec extends AbstractValidationSpec {

    void "valid object"() {
        when:
        var assertions = validateJsonWithSchema(new Llama("John", 12), "llama")

        then:
        assertions.size() == 0
    }

    void "invalid object"() {
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

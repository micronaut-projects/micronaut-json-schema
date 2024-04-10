package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.validation.JsonSchemaValidator
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ObjectsValidationSpec extends Specification {

    @Inject
    JsonSchemaValidator jsonSchemaValidator

    void "valid object"() {
        when:
        var assertions = jsonSchemaValidator.validate(new Llama("John", 12), Llama)

        then:
        assertions.size() == 0
    }

    void "invalid object"() {
        when:
        var assertions = jsonSchemaValidator.validate(llama, Llama)

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
        var assertions = jsonSchemaValidator.validate(bird, RWBlackbird)

        then:
        assertions.size() == 0
    }

    void "invalid object with changed path"() {
        when:
        var bird = '{"name":12}'
        var assertions = jsonSchemaValidator.validate(bird, RWBlackbird)

        then:
        assertions.size() == 1
        assertions[0].message == "/name: integer found, [string] expected"
    }

}

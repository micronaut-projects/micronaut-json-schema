package io.micronaut.jsonschema.validation;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Runs the JSON Schema validator TCK against the built-in validator.
 */
@Suite
@SelectPackages("io.micronaut.jsonschema.validation.tck")
@SuiteDisplayName("JSON Schema validator TCK (built-in)")
class ValidatorTckTest {
}

package io.micronaut.jsonschema.validation.networknt;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Runs the JSON Schema validator TCK against the NetworkNT based validator. The tests of the JSON-Schema-Test-Suite
 * that NetworkNT does not pass are listed in {@code json-schema-validator-tck-exclusions.txt}.
 */
@SuppressWarnings("java:S2187") // the tests are selected by @SelectPackages
@Suite
@SelectPackages("io.micronaut.jsonschema.validation.tck")
@SuiteDisplayName("JSON Schema validator TCK (NetworkNT)")
class NetworkNtTckTest {
}

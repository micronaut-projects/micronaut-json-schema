package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Test fixture bean participating in a constructor-based circular dependency (A -> B).
 */
@Context
@Requires(env = "di-validator-test")
public final class FixtureCircularBeanA {
    @Inject
    FixtureCircularBeanA(FixtureCircularBeanB beanB) {
    }
}

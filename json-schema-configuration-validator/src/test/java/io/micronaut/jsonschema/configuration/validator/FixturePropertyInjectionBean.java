package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(env = "di-validator-test")
public final class FixturePropertyInjectionBean {
    @Property(name = "spec.missing.property")
    String value;
}

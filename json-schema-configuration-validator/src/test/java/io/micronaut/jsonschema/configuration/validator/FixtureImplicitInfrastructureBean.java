package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.value.PropertyResolver;

@Context
@Requires(env = "di-validator-test")
@Requires(property = "spec.name", value = "implicit-infrastructure")
public final class FixtureImplicitInfrastructureBean {
    FixtureImplicitInfrastructureBean(Environment environment, PropertyResolver propertyResolver) {
    }
}

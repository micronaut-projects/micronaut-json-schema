package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/**
 * Test fixture controller used to validate constructor dependency checks for controller roots.
 */
@Controller("/fixture")
@Requires(env = "di-validator-test")
public final class FixtureControllerBean {
    FixtureControllerBean(FixtureMissingDependency missingDependency) {
    }

    @Get("/")
    String index() {
        return "ok";
    }
}

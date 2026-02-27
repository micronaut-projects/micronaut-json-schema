package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

/**
 * Fixture {@code @EachProperty} configuration emulating datasource-style named entries.
 */
@EachProperty(value = "datasources", primary = "default")
@Requires(env = "di-validator-test")
public final class FixtureDataSourceConfiguration {
    private final String name;
    private String url;

    FixtureDataSourceConfiguration(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

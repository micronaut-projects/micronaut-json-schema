package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@EachBean(FixtureDataSourceBean.class)
@Requires(env = "di-validator-test")
public final class FixtureDataSourceConfigurerBean implements FixtureDataSourceConfigurer {
    FixtureDataSourceConfigurerBean(FixtureDataSourceBean dataSourceBean) {
    }
}

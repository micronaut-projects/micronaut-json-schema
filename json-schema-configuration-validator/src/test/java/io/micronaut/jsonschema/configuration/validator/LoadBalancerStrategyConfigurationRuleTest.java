/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.reflect.ClassUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadBalancerStrategyConfigurationRuleTest {

    private static final String PREFIX = "micronaut.http.services.orders";

    private static Environment environment;

    @BeforeAll
    static void start() {
        ClassLoader classLoader = LoadBalancerStrategyConfigurationRuleTest.class.getClassLoader();
        environment = Environment.create(new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return List.of("test");
            }

            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }

            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }

            @Override
            public boolean isEnableDefaultPropertySources() {
                return false;
            }
        }).start();
    }

    @AfterAll
    static void stop() {
        environment.stop();
    }

    @Test
    void supportsServiceEntriesOnly() {
        LoadBalancerStrategyConfigurationRule rule = new LoadBalancerStrategyConfigurationRule();
        assertTrue(rule.supportsPrefix(PREFIX));
        assertFalse(rule.supportsPrefix("micronaut.http.services"));
        assertFalse(rule.supportsPrefix("micronaut.http.services."));
        assertFalse(rule.supportsPrefix(PREFIX + ".pool"));
        assertFalse(rule.supportsPrefix("micronaut.http.client"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"round-robin", "random", "p2c", "weighted", "sticky", "Round-Robin", "P2C", " sticky "})
    void acceptsBuiltInStrategies(String name) {
        assertTrue(validate(fixtureRule(), name).isEmpty());
    }

    @Test
    void acceptsNamedStrategyBean() {
        assertTrue(validate(fixtureRule(), "least-loaded").isEmpty());
    }

    @Test
    void rejectsUnknownStrategy() {
        Set<ConfigurationError> errors = validate(fixtureRule(), "fastest");

        assertEquals(1, errors.size(), () -> "Expected one error, got: " + errors);
        ConfigurationError error = errors.iterator().next();
        assertEquals(PREFIX + ".load-balancer-strategy", error.property());
        assertEquals(
            "Unknown load balancer strategy 'fastest': expected one of round-robin, random, p2c, weighted, sticky, or one of the LoadBalancerStrategy beans least-loaded",
            error.message()
        );
    }

    @Test
    void namedBeanMatchIsCaseSensitive() {
        assertEquals(1, validate(fixtureRule(), "Least-Loaded").size());
    }

    @Test
    void ignoresMissingOrBlankStrategy() {
        LoadBalancerStrategyConfigurationRule rule = fixtureRule();
        assertTrue(rule.validate(context(Map.of("url", "http://localhost"))).isEmpty());
        assertTrue(validate(rule, " ").isEmpty());
    }

    @Test
    void inactiveWithoutStrategySupport() {
        LoadBalancerStrategyConfigurationRule rule = new LoadBalancerStrategyConfigurationRule("io.micronaut.test.MissingStrategy");
        assertTrue(validate(rule, "fastest").isEmpty());
    }

    @Test
    void validatorRunsRuleForServiceEntries() {
        Environment env = Environment.create(new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return List.of("test");
            }

            @Override
            public ClassLoader getClassLoader() {
                return LoadBalancerStrategyConfigurationRuleTest.class.getClassLoader();
            }

            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }

            @Override
            public boolean isEnableDefaultPropertySources() {
                return false;
            }
        });
        env.addPropertySource(PropertySource.of("test", Map.of(
            PREFIX + ".url", "http://localhost:8080",
            PREFIX + ".load-balancer-strategy", "fastest"
        ), PropertySource.Origin.of("test-origin")));
        env.start();
        try {
            ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
            validator.setFailOnNotPresent(false);
            Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);
            boolean strategySupported = ClassUtils.isPresent(LoadBalancerStrategyConfigurationRule.STRATEGY_TYPE, getClass().getClassLoader());
            boolean reported = errors.stream().anyMatch(e -> e.property().equals(PREFIX + ".load-balancer-strategy")
                && e.message().startsWith("Unknown load balancer strategy 'fastest'"));
            assertEquals(strategySupported, reported, () -> "Unexpected errors: " + errors);
        } finally {
            env.stop();
        }
    }

    private static LoadBalancerStrategyConfigurationRule fixtureRule() {
        return new LoadBalancerStrategyConfigurationRule(FixtureLoadBalancerStrategy.class.getName());
    }

    private static Set<ConfigurationError> validate(LoadBalancerStrategyConfigurationRule rule, String strategy) {
        return rule.validate(context(Map.of("load-balancer-strategy", strategy)));
    }

    private static ConfigurationValidationContext context(Map<String, Object> instance) {
        return new ConfigurationValidationContext(environment, PREFIX, null, null, instance);
    }
}

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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates {@code micronaut.http.services.<id>.load-balancer-strategy} against the built-in
 * load balancer strategies and the names of {@code @Named} {@code LoadBalancerStrategy} beans.
 * <p>
 * The rule is inactive when the HTTP client on the classpath does not support load balancer strategies.
 */
@Internal
public final class LoadBalancerStrategyConfigurationRule implements ConfigurationRule {

    static final String STRATEGY_TYPE = "io.micronaut.http.client.loadbalance.LoadBalancerStrategy";
    static final String PROPERTY = "load-balancer-strategy";
    static final List<String> BUILT_IN = List.of("round-robin", "random", "p2c", "weighted", "sticky");

    private static final String SERVICES_PREFIX = "micronaut.http.services.";

    private final String strategyType;
    private @Nullable Set<String> beanNames;

    /**
     * Default constructor used by the service loader.
     */
    public LoadBalancerStrategyConfigurationRule() {
        this(STRATEGY_TYPE);
    }

    LoadBalancerStrategyConfigurationRule(String strategyType) {
        this.strategyType = strategyType;
    }

    @Override
    public boolean supportsPrefix(String prefix) {
        return prefix.startsWith(SERVICES_PREFIX)
            && prefix.length() > SERVICES_PREFIX.length()
            && prefix.indexOf('.', SERVICES_PREFIX.length()) == -1;
    }

    @Override
    public Set<ConfigurationError> validate(ConfigurationValidationContext context) {
        if (!(context.instanceMap().get(PROPERTY) instanceof CharSequence value)) {
            return Set.of();
        }
        String name = value.toString().trim();
        if (name.isEmpty() || BUILT_IN.contains(name.toLowerCase(Locale.ROOT))) {
            return Set.of();
        }
        Set<String> names = beanNames(context.environment().getClassLoader());
        if (names == null || names.contains(name)) {
            return Set.of();
        }
        String property = context.prefix() + "." + PROPERTY;
        StringBuilder message = new StringBuilder("Unknown load balancer strategy '")
            .append(name)
            .append("': expected one of ")
            .append(String.join(", ", BUILT_IN));
        if (!names.isEmpty()) {
            message.append(", or one of the LoadBalancerStrategy beans ").append(String.join(", ", names));
        } else {
            message.append(", or the name of a LoadBalancerStrategy bean");
        }
        return Set.of(ConfigurationError.builder(property, message.toString())
            .rawPropertyName(property)
            .rawValue(value)
            .build());
    }

    /**
     * Resolves the names of the {@code @Named} strategy beans.
     *
     * @param classLoader The class loader
     * @return The names, or null if strategies are not supported
     */
    private @Nullable Set<String> beanNames(ClassLoader classLoader) {
        Set<String> names = beanNames;
        if (names != null) {
            return names;
        }
        Class<?> type = ClassUtils.forName(strategyType, classLoader).orElse(null);
        if (type == null) {
            return null;
        }
        Argument<?> argument = Argument.of(type);
        names = new TreeSet<>();
        for (BeanDefinitionReference<?> reference : MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(
            classLoader,
            BeanDefinitionReference.class,
            BeanDefinitionReference::isPresent
        )) {
            if (!reference.isCandidateBean(argument)) {
                continue;
            }
            String named = reference.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).orElse(null);
            if (named != null && !named.isBlank()) {
                names.add(named);
            }
        }
        beanNames = names;
        return names;
    }
}

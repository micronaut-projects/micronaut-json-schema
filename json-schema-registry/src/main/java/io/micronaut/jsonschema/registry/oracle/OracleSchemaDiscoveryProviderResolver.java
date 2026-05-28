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
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.context.BeanContext;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Oracle authority discovery providers from Micronaut beans.
 *
 * @since 2.0.0
 */
@Singleton
public final class OracleSchemaDiscoveryProviderResolver {
    private static final Map<String, Class<? extends OracleSchemaDiscoveryProvider>> BUILTIN_PROVIDER_BEANS = Map.of(
        OracleDomainDiscoveryProvider.class.getName(), OracleDomainDiscoveryProvider.class,
        io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider.class.getName(), OracleDomainDiscoveryProvider.class,
        io.micronaut.jsonschema.generator.oracle.OracleDualityJsonViewDiscoveryProvider.class.getName(), OracleDualityJsonViewDiscoveryProvider.class
    );

    private final BeanContext beanContext;

    /**
     * @param beanContext Bean context
     */
    public OracleSchemaDiscoveryProviderResolver(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * Resolve a provider class name.
     *
     * @param providerClassName Provider class name
     * @return The provider instance
     */
    public OracleSchemaDiscoveryProvider resolve(String providerClassName) {
        Class<? extends OracleSchemaDiscoveryProvider> builtinProviderBean = BUILTIN_PROVIDER_BEANS.get(providerClassName);
        if (builtinProviderBean != null) {
            return beanContext.getBean(builtinProviderBean);
        }
        return beanContext.getBeansOfType(OracleSchemaDiscoveryProvider.class)
            .stream()
            .filter(provider -> provider.getClass().getName().equals(providerClassName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unable to locate Oracle discovery provider bean: " + providerClassName
                    + ". Available providers: " + availableProviders()
            ));
    }

    private List<String> availableProviders() {
        Set<String> availableProviders = new LinkedHashSet<>(BUILTIN_PROVIDER_BEANS.keySet());
        beanContext.getBeansOfType(OracleSchemaDiscoveryProvider.class)
            .stream()
            .map(provider -> provider.getClass().getName())
            .sorted()
            .forEach(availableProviders::add);
        return availableProviders.stream().sorted().toList();
    }
}

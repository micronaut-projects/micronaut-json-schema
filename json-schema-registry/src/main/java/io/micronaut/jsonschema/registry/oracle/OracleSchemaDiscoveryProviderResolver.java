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

import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves Oracle authority discovery providers from Micronaut beans or {@link ServiceLoader}.
 *
 * @since 2.0.0
 */
@Singleton
public final class OracleSchemaDiscoveryProviderResolver {
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
     * @param classLoader ClassLoader
     * @return The provider instance
     */
    public OracleSchemaDiscoveryProvider resolve(String providerClassName, ClassLoader classLoader) {
        Optional<OracleSchemaDiscoveryProvider> beanProvider = beanContext.getBeansOfType(OracleSchemaDiscoveryProvider.class)
            .stream()
            .filter(provider -> provider.getClass().getName().equals(providerClassName))
            .findFirst();
        if (beanProvider.isPresent()) {
            return beanProvider.get();
        }
        try {
            ServiceLoader<OracleSchemaDiscoveryProvider> loader = ServiceLoader.load(OracleSchemaDiscoveryProvider.class, classLoader);
            return loader.stream()
                .filter(loadedProvider -> loadedProvider.type().getName().equals(providerClassName))
                .findFirst()
                .map(ServiceLoader.Provider::get)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unable to locate Oracle discovery provider: " + providerClassName
                        + ". Available providers: " + availableProviders(loader)
                ));
        } catch (ServiceConfigurationError e) {
            throw new IllegalArgumentException("Unable to load Oracle discovery provider: " + providerClassName, e);
        }
    }

    private static List<String> availableProviders(ServiceLoader<OracleSchemaDiscoveryProvider> loader) {
        return loader.stream()
            .map(provider -> provider.type().getName())
            .sorted()
            .toList();
    }
}

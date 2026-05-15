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
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves Oracle materializers from Micronaut beans or {@link ServiceLoader}.
 *
 * @since 2.0.0
 */
@Singleton
public final class OracleSchemaMaterializerResolver {
    private final BeanContext beanContext;

    /**
     * @param beanContext Bean context
     */
    public OracleSchemaMaterializerResolver(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * Resolve a materializer class name.
     *
     * @param providerClassName Materializer class name
     * @param classLoader ClassLoader
     * @return The materializer instance
     */
    public OracleSchemaMaterializer resolve(String providerClassName, ClassLoader classLoader) {
        Optional<OracleSchemaMaterializer> beanMaterializer = beanContext.getBeansOfType(OracleSchemaMaterializer.class)
            .stream()
            .filter(materializer -> materializer.providerClassName().equals(providerClassName)
                || materializer.getClass().getName().equals(providerClassName))
            .findFirst();
        if (beanMaterializer.isPresent()) {
            return beanMaterializer.get();
        }
        try {
            ServiceLoader<OracleSchemaMaterializer> loader = ServiceLoader.load(OracleSchemaMaterializer.class, classLoader);
            return loader.stream()
                .filter(materializer -> materializer.type().getName().equals(providerClassName))
                .findFirst()
                .map(ServiceLoader.Provider::get)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unable to locate Oracle materializer: " + providerClassName
                        + ". Available materializers: " + availableMaterializers(loader)
                ));
        } catch (ServiceConfigurationError e) {
            throw new IllegalArgumentException("Unable to load Oracle materializer: " + providerClassName, e);
        }
    }

    private static List<String> availableMaterializers(ServiceLoader<OracleSchemaMaterializer> loader) {
        return loader.stream()
            .map(materializer -> materializer.type().getName())
            .sorted()
            .toList();
    }
}

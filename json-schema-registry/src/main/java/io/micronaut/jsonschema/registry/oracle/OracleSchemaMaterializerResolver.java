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

/**
 * Resolves Oracle materializers from Micronaut beans.
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
     * @return The materializer instance
     */
    public OracleSchemaMaterializer resolve(String providerClassName) {
        return beanContext.getBeansOfType(OracleSchemaMaterializer.class)
            .stream()
            .filter(materializer -> materializer.providerClassName().equals(providerClassName)
                || materializer.getClass().getName().equals(providerClassName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unable to locate Oracle materializer bean: " + providerClassName
                    + ". Available materializers: " + availableMaterializers()
            ));
    }

    private List<String> availableMaterializers() {
        return beanContext.getBeansOfType(OracleSchemaMaterializer.class)
            .stream()
            .map(materializer -> materializer.getClass().getName())
            .sorted()
            .toList();
    }
}

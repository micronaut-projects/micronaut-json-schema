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
package io.micronaut.jsonschema.generator.discovery;

import io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleDualityViewSchemaDiscoveryProvider;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Resolves schema discovery providers from built-in registrations or the provider classpath.
 */
public final class SchemaDiscoveryProviders {

    private static final List<Supplier<SchemaDiscoveryProvider>> BUILT_IN_PROVIDERS = List.of(
        OracleDomainSchemaDiscoveryProvider::new,
        OracleDualityViewSchemaDiscoveryProvider::new
    );

    private SchemaDiscoveryProviders() {
    }

    /**
     * Resolve a configured discovery provider.
     *
     * @param provider The configured provider id
     * @param classLoader The classloader used for custom provider service loading
     * @return The resolved provider
     */
    public static SchemaDiscoveryProvider resolve(String provider, ClassLoader classLoader) {
        for (Supplier<SchemaDiscoveryProvider> providerSupplier : BUILT_IN_PROVIDERS) {
            SchemaDiscoveryProvider candidate = providerSupplier.get();
            if (candidate.providerId().equals(provider)) {
                return candidate;
            }
        }
        for (SchemaDiscoveryProvider candidate : ServiceLoader.load(SchemaDiscoveryProvider.class, classLoader)) {
            if (candidate.providerId().equals(provider)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unable to locate schema discovery provider: " + provider);
    }
}

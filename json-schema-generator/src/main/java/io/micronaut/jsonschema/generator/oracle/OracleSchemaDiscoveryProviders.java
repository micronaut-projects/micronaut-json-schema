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
package io.micronaut.jsonschema.generator.oracle;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class OracleSchemaDiscoveryProviders {

    private OracleSchemaDiscoveryProviders() {
    }

    static OracleSchemaDiscoveryProvider resolve(String providerClassName, ClassLoader classLoader) {
        try {
            ServiceLoader<OracleSchemaDiscoveryProvider> loader = ServiceLoader.load(OracleSchemaDiscoveryProvider.class, classLoader);
            return loader.stream()
                .filter(provider -> provider.type().getName().equals(providerClassName))
                .findFirst()
                .map(ServiceLoader.Provider::get)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unable to locate Oracle discovery provider: " + providerClassName
                        + ". Ensure it is registered in META-INF/services/"
                        + OracleSchemaDiscoveryProvider.class.getName()
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

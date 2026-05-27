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

final class SchemaDiscoveryProviders {

    private SchemaDiscoveryProviders() {
    }

    static SchemaDiscoveryProvider resolve(String providerClassName, ClassLoader classLoader) {
        try {
            Class<?> providerClass = Class.forName(providerClassName, true, classLoader);
            Class<? extends SchemaDiscoveryProvider> providerType = providerClass.asSubclass(SchemaDiscoveryProvider.class);
            return providerType.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to locate schema discovery provider class: " + providerClassName, e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Schema discovery provider does not implement " + SchemaDiscoveryProvider.class.getName() + ": " + providerClassName, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to instantiate schema discovery provider: " + providerClassName + ". Ensure it exposes an accessible no-argument constructor.", e);
        }
    }
}

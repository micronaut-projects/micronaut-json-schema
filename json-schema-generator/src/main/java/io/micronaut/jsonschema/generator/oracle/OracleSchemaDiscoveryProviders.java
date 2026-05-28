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

import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

import java.util.List;
import java.util.stream.StreamSupport;

final class OracleSchemaDiscoveryProviders {

    private OracleSchemaDiscoveryProviders() {
    }

    static OracleSchemaDiscoveryProvider resolve(String providerClassName, ClassLoader classLoader) {
        return StreamSupport.stream(
                SoftServiceLoader.load(OracleSchemaDiscoveryProvider.class, classLoader).spliterator(),
                false
            )
            .filter(ServiceDefinition::isPresent)
            .filter(definition -> definition.getName().equals(providerClassName))
            .findFirst()
            .map(ServiceDefinition::load)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unable to locate Oracle discovery provider: " + providerClassName
                    + ". Ensure it is registered in META-INF/services/"
                    + OracleSchemaDiscoveryProvider.class.getName()
                    + ". Available providers: " + availableProviders(classLoader)
            ));
    }

    private static List<String> availableProviders(ClassLoader classLoader) {
        return StreamSupport.stream(
                SoftServiceLoader.load(OracleSchemaDiscoveryProvider.class, classLoader).spliterator(),
                false
            )
            .map(ServiceDefinition::getName)
            .sorted()
            .toList();
    }
}

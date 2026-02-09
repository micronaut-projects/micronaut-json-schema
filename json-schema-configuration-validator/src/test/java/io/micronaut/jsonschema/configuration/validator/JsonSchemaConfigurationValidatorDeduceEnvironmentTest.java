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
import io.micronaut.jsonschema.configuration.validator.cli.JsonSchemaConfigurationValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaConfigurationValidatorDeduceEnvironmentTest {

    @Test
    void createEnvironmentConfigurationUsesBuilderDeduceEnvironmentValue() throws Exception {
        Method m = JsonSchemaConfigurationValidator.class.getDeclaredMethod(
            "createEnvironmentConfiguration",
            ClassLoader.class,
            List.class,
            boolean.class
        );
        m.setAccessible(true);

        ApplicationContextConfiguration cfgTrue = (ApplicationContextConfiguration) m.invoke(
            null,
            getClass().getClassLoader(),
            List.of("test"),
            true
        );
        assertTrue(cfgTrue.getDeduceEnvironments().orElseThrow());

        ApplicationContextConfiguration cfgFalse = (ApplicationContextConfiguration) m.invoke(
            null,
            getClass().getClassLoader(),
            List.of("test"),
            false
        );
        assertFalse(cfgFalse.getDeduceEnvironments().orElseThrow());
    }
}

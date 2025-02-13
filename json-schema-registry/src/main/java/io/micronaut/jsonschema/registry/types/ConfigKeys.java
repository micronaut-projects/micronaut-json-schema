/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.registry.types;

public final class ConfigKeys {
    public static final String CONFIG_NAME = "schema.registry";
    public static final String ORIGIN = CONFIG_NAME + ".origin";
    public static final String USERNAME = CONFIG_NAME + ".username";
    public static final String PASSWORD = CONFIG_NAME + ".password";
    public static final String BASIC_AUTH_ENABLED = CONFIG_NAME + ".basicAuthEnabled";
    public static final String PUSH_TO_REGISTRY_ENABLED = CONFIG_NAME + ".pushToRegistryEnabled";
}

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
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.registry.types.ConfigKeys;

/**
 * A client for the Confluent Json Schema Registry without authentication.
 *
 * <p> Supports the operations defined in the
 * <a href="https://docs.confluent.io/platform/current/schema-registry/develop/api.html">SchemaJson Registry API</a>.
 *
 * <p> The client is configured with the {@link SchemaRegistryConfig} file for its host address.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Client(value = "${" + ConfigKeys.ORIGIN + "}")
@Requires(beans = SchemaRegistryConfig.class)
@Requires(property = ConfigKeys.ORIGIN)
@Consumes(MediaType.APPLICATION_JSON)
public interface SchemaRegistryNoAuthClient extends SchemaRegistryClient {
}

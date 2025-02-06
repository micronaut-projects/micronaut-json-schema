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

import jakarta.inject.Singleton;

@Singleton
public class SchemaRegistryService {
    private final SchemaRegistryClient schemaRegistryClient;

    public SchemaRegistryService(SchemaRegistryClient schemaRegistryClient) {
        this.schemaRegistryClient = schemaRegistryClient;
    }

//    public void registerSchema(String subject, String schema) {
//        int schemaId = schemaRegistryClient.registerSchema(subject, schema);
//        System.out.println("Registered schema with ID: " + schemaId);
//    }
//
//    public String getSchema(String subject, int version) {
//        return schemaRegistryClient.getSchema(subject, version);
//    }
//
//    public boolean checkSchemaCompatibility(String subject, String schema) {
//        return schemaRegistryClient.checkCompatibility(subject, schema);
//    }

}

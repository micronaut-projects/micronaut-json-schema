/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.jsonschema.validation;

import com.networknt.schema.SpecVersion;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ExecutionContextCustomizer;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.jsonschema.utils.JsonSchemaConfiguration;
import io.micronaut.jsonschema.utils.JsonSchemaResourceUtils;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
@Internal
final class DefaultJsonSchemaValidator implements JsonSchemaValidator {
    private static final ExecutionContextCustomizer CONTEXT_CUSTOMIZER = (executionContext, validationContext) -> {
        // By default, since Draft 2019-09 the format keyword only generates annotations and not assertions
        validationContext.getConfig().setFormatAssertionsEnabled(true);
    };

    private final Map<Class<?>, JsonSchema> jsonSchemaCache = new ConcurrentHashMap<>();
    private final JsonSchemaValidatorConfiguration config;
    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final SchemaValidatorsConfig schemaValidatorsConfig;
    private final JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader;
    private final JsonSchemaConfiguration jsonSchemaConfiguration;

    DefaultJsonSchemaValidator(
        JsonSchemaValidatorConfiguration config,
        ResourceLoader resourceLoader,
        JsonMapper jsonMapper,
        SchemaValidatorsConfig schemaValidatorsConfig,
        JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader,
        JsonSchemaConfiguration jsonSchemaConfiguration
    ) {
        this.config = config;
        this.resourceLoader = resourceLoader;
        this.jsonMapper = jsonMapper;
        this.schemaValidatorsConfig = schemaValidatorsConfig;
        this.jsonSchemaClassPathResourceLoader = jsonSchemaClassPathResourceLoader;
        this.jsonSchemaConfiguration = jsonSchemaConfiguration;
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(@NonNull String json, @NonNull Class<T> type) {
        JsonSchema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, json);
    }

    @Override
    @NonNull
    public Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Map<String, Object> jsonSchema) throws IOException {
        JsonSchema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    @NonNull
    public Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull String jsonSchema) throws IOException {
        JsonSchema schema = jsonSchema(jsonSchema);
        return validate(schema, value);
    }

    @Override
    @NonNull
    public <T> Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Class<T> type) throws IOException {
        JsonSchema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchema);
        return validate(schema, value);
    }

    private <T> JsonSchema jsonSchema(@NonNull Class<T> type) {
        String jsonSchema = jsonSchemaClassPathResourceLoader.jsonSchemaStringForClass(type).orElse(null);
        if (jsonSchema == null) {
            throw new IllegalArgumentException("No schema found for type: " + type);
        }
        return jsonSchema(jsonSchema);
    }

    @NonNull
    private JsonSchema jsonSchema(@NonNull Map<String, Object> jsonSchema) {
        try {
            String jsonSchemaString = jsonMapper.writeValueAsString(jsonSchema);
            return jsonSchema(jsonSchemaString);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not serialize JSON Schema from: " + jsonSchema);
        }
    }

    @NonNull
    private JsonSchema jsonSchema(@NonNull String jsonSchema) {
        JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> {
            builder.schemaLoaders(b -> b.add(new ResourceSchemaLoader()));
        });
        return jsonSchemaFactory.getSchema(jsonSchema, schemaValidatorsConfig);
    }

    private Set<? extends ValidationMessage> validate(JsonSchema schema, Object value) throws IOException {
        String json = value instanceof String s ? s : jsonMapper.writeValueAsString(value);
        return validate(schema, json);
    }

    private static Set<? extends ValidationMessage> validate(JsonSchema schema, String json) {
        return schema.validate(json, InputFormat.JSON, CONTEXT_CUSTOMIZER)
            .stream()
            .map(ValidationMessageAdapter::new)
            .collect(Collectors.toSet());
    }

    private final class ResourceSchemaLoader implements SchemaLoader {
        @Override
        public InputStreamSource getSchema(AbsoluteIri absoluteIri) {
            String path = URI.create(absoluteIri.toString()).toString();
            if (path.startsWith(config.baseUri())) {
                path = path.substring(config.baseUri().length());
            }
            String classpathFolder = JsonSchemaResourceUtils.generatedSchemasFolder(jsonSchemaConfiguration);
            String filePath = JsonSchemaResourceUtils.resolvePathWithinFolder(classpathFolder, path, absoluteIri.toString(), config.classpathFolder());
            return () -> resourceLoader.getResourceAsStream(JsonSchemaResourceUtils.CLASSPATH_PREFIX + filePath)
                .orElseThrow(() -> new IllegalArgumentException("No schema found for uri: " + absoluteIri + " at path: " + filePath));
        }
    }

}

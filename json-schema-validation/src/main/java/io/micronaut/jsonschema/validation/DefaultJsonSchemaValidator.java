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

import com.networknt.schema.*;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
@Internal
final class DefaultJsonSchemaValidator implements JsonSchemaValidator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaValidator.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String SUFFIX = ".schema.json";
    private static final String MEMBER_URI = "uri";
    private static final ExecutionContextCustomizer CONTEXT_CUSTOMIZER = (executionContext, validationContext) -> {
        // By default, since Draft 2019-09 the format keyword only generates annotations and not assertions
        validationContext.getConfig().setFormatAssertionsEnabled(true);
    };

    private final Map<Class<?>, JsonSchema> jsonSchemaCache = new ConcurrentHashMap<>();
    private final JsonSchemaValidatorConfiguration config;
    private final ResourceLoader resourceLoader;
    private final JsonMapper jsonMapper;
    private final SchemaValidatorsConfig schemaValidatorsConfig;

    DefaultJsonSchemaValidator(
            JsonSchemaValidatorConfiguration config,
            ResourceLoader resourceLoader,
            JsonMapper jsonMapper,
            SchemaValidatorsConfig schemaValidatorsConfig
    ) {
        this.config = config;
        this.resourceLoader = resourceLoader;
        this.jsonMapper = jsonMapper;
        this.schemaValidatorsConfig = schemaValidatorsConfig;
    }

    @Override
    public <T> Set<? extends ValidationMessage> validate(@NonNull String json, @NonNull Class<T> type) {
        JsonSchema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchemaForClass);
        return validate(schema, json);
    }

    @Override
    @NonNull
    public <T> Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Class<T> type) throws IOException {
        JsonSchema schema = jsonSchemaCache.computeIfAbsent(type, this::jsonSchemaForClass);
        String json = jsonMapper.writeValueAsString(value);
        return validate(schema, json);
    }

    private <T> JsonSchema jsonSchemaForClass(@NonNull Class<T> type) {
        String jsonSchema = jsonSchemaStringForClass(type);
        if (jsonSchema == null) {
            throw new IllegalArgumentException("No schema found for type: " + type);
        }
        JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> {
            builder.schemaLoaders(b -> b.add(new ResourceSchemaLoader()));
        });
        return jsonSchemaFactory.getSchema(jsonSchema, schemaValidatorsConfig);
    }

    private <T> String jsonSchemaStringForClass(@NonNull Class<T> type) {
        String path = jsonSchemaPath(type);
        try (InputStream inputStream = resourceLoader.getResourceAsStream(path).orElseThrow(() -> new IllegalArgumentException("No schema found for type: " + type + " at path: " + path))) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private <T> String jsonSchemaPath(@NonNull Class<T> type) {
        String className = NameUtils.hyphenate(type.getSimpleName());
        try {
            BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(type);
            AnnotationValue<io.micronaut.jsonschema.JsonSchema> jsonSchemaAnnotationValue = introspection.getAnnotation(io.micronaut.jsonschema.JsonSchema.class);
            Optional<String> uriOptional = jsonSchemaAnnotationValue.stringValue(MEMBER_URI);
            if (uriOptional.isPresent()) {
                className = uriOptional.get().replace("/", "");
            }
        } catch (IntrospectionException e) {
            LOG.debug("Introspection exception for class {}.}", type, e);
        }
        String name = className + SUFFIX;
        return CLASSPATH_PREFIX + config.classpathFolder() + name;
    }

    private static Set<? extends ValidationMessage> validate(JsonSchema schema, String json) {
        return schema.validate(json, InputFormat.JSON, CONTEXT_CUSTOMIZER)
                .stream()
                .map(ValidationMessageAdapter::new)
                .collect(Collectors.toSet());
    }

    private class ResourceSchemaLoader implements SchemaLoader {
        @Override
        public InputStreamSource getSchema(AbsoluteIri absoluteIri) {
            String path = URI.create(absoluteIri.toString()).toString();
            if (path.startsWith(config.baseUri())) {
                path = path.substring(config.baseUri().length());
            }
            String filePath = Path.of(config.classpathFolder() + path).normalize().toString();
            if (!filePath.startsWith(config.classpathFolder())) {
                throw new IllegalArgumentException("Schema for URI " + absoluteIri + " is not inside the required folder " + config.classpathFolder() + " at path: " + path);
            }
            return () -> resourceLoader.getResourceAsStream(CLASSPATH_PREFIX + filePath)
                .orElseThrow(() -> new IllegalArgumentException("No schema found for uri: " + absoluteIri + " at path: " + filePath));
        }
    }

}

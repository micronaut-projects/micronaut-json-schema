package io.micronaut.jsonschema.utils;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
@Property(name = "spec.name", value = "CompositeJsonSchemaClassPathResourceLoaderTest")
class CompositeJsonSchemaClassPathResourceLoaderTest {

    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE) // This loader will be consulted first and throws
    @Requires(property = "spec.name", value = "CompositeJsonSchemaClassPathResourceLoaderTest")
    static class ThrowingLoader implements JsonSchemaClassPathResourceLoader {
        @Override
        public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
            throw new RuntimeException("boom");
        }
    }

    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE + 10) // Next loader returns a value
    @Requires(property = "spec.name", value = "CompositeJsonSchemaClassPathResourceLoaderTest")
    static class ProvidingLoader implements JsonSchemaClassPathResourceLoader {
        @Override
        public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
            if (type == Test.class) {
                return Optional.of("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"string\"}");
            }
            return Optional.empty();
        }
    }

    @Test
    void compositeReturnsFirstAvailableResult(JsonSchemaClassPathResourceLoader resourceLoader) {
        Optional<String> result = resourceLoader.jsonSchemaStringForClass(Test.class);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("$schema"));
    }
}

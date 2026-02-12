package io.micronaut.jsonschema.utils;

import io.micronaut.core.io.Readable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaClassPathResourceLoaderCompositeFactoryTest {

    @Test
    void createCompositeSkipsNullLoadersAndShortCircuitsOnFirstPresent() throws Exception {
        AtomicInteger calls1 = new AtomicInteger();
        AtomicInteger calls2 = new AtomicInteger();

        JsonSchemaClassPathResourceLoader l1 = new JsonSchemaClassPathResourceLoader() {
            @Override
            public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
                calls1.incrementAndGet();
                return Optional.empty();
            }
        };
        JsonSchemaClassPathResourceLoader l2 = new JsonSchemaClassPathResourceLoader() {
            @Override
            public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
                calls2.incrementAndGet();
                return Optional.of("schema");
            }
        };

        JsonSchemaClassPathResourceLoader composite = invokeCreateComposite(null, l1, l2);

        assertTrue(composite.jsonSchemaStringForClass(String.class).isPresent());
        assertEquals(1, calls1.get());
        assertEquals(1, calls2.get());
    }

    @Test
    void createCompositeReturnsEmptyWhenNoLoaderProvidesSchema() throws Exception {
        JsonSchemaClassPathResourceLoader l1 = new JsonSchemaClassPathResourceLoader() {
            @Override
            public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
                return Optional.empty();
            }
        };

        JsonSchemaClassPathResourceLoader composite = invokeCreateComposite(l1);

        assertTrue(composite.jsonSchemaStringForClass(String.class).isEmpty());
    }

    @Test
    void createCompositeMergesJsonSchemasWithPutIfAbsent() throws Exception {
        Readable r1 = new FixedReadable("a");
        Readable r2 = new FixedReadable("b");
        Readable r3 = new FixedReadable("c");

        JsonSchemaClassPathResourceLoader l1 = new JsonSchemaClassPathResourceLoader() {
            @Override
            public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
                return Optional.empty();
            }

            @Override
            public Map<String, Readable> jsonSchemas() {
                Map<String, Readable> m = new LinkedHashMap<>();
                m.put("a.json", r1);
                m.put("shared.json", r2);
                return m;
            }
        };

        JsonSchemaClassPathResourceLoader l2 = new JsonSchemaClassPathResourceLoader() {
            @Override
            public <T> Optional<String> jsonSchemaStringForClass(Class<T> type) {
                return Optional.empty();
            }

            @Override
            public Map<String, Readable> jsonSchemas() {
                Map<String, Readable> m = new LinkedHashMap<>();
                m.put("shared.json", r3);
                m.put("b.json", r3);
                return m;
            }
        };

        JsonSchemaClassPathResourceLoader composite = invokeCreateComposite(l1, l2);
        Map<String, Readable> merged = composite.jsonSchemas();

        assertSame(r1, merged.get("a.json"));
        assertSame(r2, merged.get("shared.json"));
        assertSame(r3, merged.get("b.json"));
    }

    private static JsonSchemaClassPathResourceLoader invokeCreateComposite(JsonSchemaClassPathResourceLoader... loaders) throws Exception {
        Method m = JsonSchemaClassPathResourceLoader.class.getDeclaredMethod("createComposite", JsonSchemaClassPathResourceLoader[].class);
        m.setAccessible(true);
        return (JsonSchemaClassPathResourceLoader) m.invoke(null, (Object) loaders);
    }

    private record FixedReadable(String text) implements Readable {
        @Override
        public String getName() {
            return "fixed";
        }

        @Override
        public InputStream asInputStream() {
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean exists() {
            return true;
        }
    }
}

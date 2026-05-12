/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal.generator;

import com.sun.net.httpserver.HttpServer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Gradle shared build service that serves static JSON files over HTTP using
 * the JDK built-in {@link HttpServer}. Used by the schema generator tasks to
 * resolve {@code $ref} URLs that would otherwise depend on remote services
 * (e.g. {@code schemastore.org}), keeping builds reproducible and offline-safe.
 *
 * <p>The server binds to an ephemeral port and serves every file under
 * {@link Params#getFixturesDir() fixturesDir} as {@code application/json}.
 * Tasks consuming this service can read {@link #getBaseUrl()} to construct
 * full URLs (e.g. {@code http://localhost:<port>/base.json}).
 */
public abstract class MockSchemaServerService
    implements BuildService<MockSchemaServerService.Params>, AutoCloseable {

    public interface Params extends BuildServiceParameters {
        DirectoryProperty getFixturesDir();
        Property<Integer> getPort();
    }

    private final HttpServer server;

    public MockSchemaServerService() throws IOException {
        Path fixturesDir = getParameters().getFixturesDir().get().getAsFile().toPath();
        int port = getParameters().getPort().getOrElse(0);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.createContext("/", exchange -> {
            try {
                String requestPath = exchange.getRequestURI().getPath();
                String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
                Path candidate = fixturesDir.resolve(relative).normalize();
                if (!candidate.startsWith(fixturesDir) || !Files.isRegularFile(candidate)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = Files.readAllBytes(candidate);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } finally {
                exchange.close();
            }
        });
        this.server.start();
    }

    public String getBaseUrl() {
        InetSocketAddress address = server.getAddress();
        return "http://" + address.getHostString() + ":" + address.getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

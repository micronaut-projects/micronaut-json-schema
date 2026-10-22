package io.micronaut.jsonschema.validation.tck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Locates the vendored <a href="https://github.com/json-schema-org/JSON-Schema-Test-Suite">JSON-Schema-Test-Suite</a>,
 * whether the TCK is on the classpath as a directory or as a jar.
 */
public final class TestSuiteFiles {

    private TestSuiteFiles() {
    }

    /**
     * @return The root directory of the test suite (containing {@code tests} and {@code remotes})
     */
    public static Path root() {
        URL url = Objects.requireNonNull(TestSuiteFiles.class.getResource("/JSON-Schema-Test-Suite/LICENSE"),
            "JSON-Schema-Test-Suite not found on the classpath");
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem fileSystem;
                try {
                    fileSystem = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    fileSystem = FileSystems.getFileSystem(uri);
                }
                return fileSystem.getPath("/JSON-Schema-Test-Suite");
            }
            return Path.of(uri).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

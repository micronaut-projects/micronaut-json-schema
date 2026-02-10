package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".endpoint.enabled", value = StringUtils.FALSE)
@Property(name = "endpoints.configurationerrors.enabled", value = StringUtils.TRUE)
@Property(name = "test.config.enabled", value = "not-a-bool")
@Property(name = "test.config.count", value = "1")
@MicronautTest
class ConfigurationEndpointDisabledTest {
    @Test
    void configurationEndpointCanBeDisabled(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/configurationerrors")));
    }
}

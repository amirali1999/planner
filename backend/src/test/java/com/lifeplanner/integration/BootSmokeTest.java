package com.lifeplanner.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the real embedded server on a random port and calls it over a loopback socket. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    ServerProperties serverProperties;

    @Test
    void defaultBindAddressIsLoopback() {
        assertThat(serverProperties.getAddress()).isNotNull();
        assertThat(serverProperties.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void healthOverRealSocket() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"UP\",\"apiVersion\":\"v1\"}");
        }
    }
}

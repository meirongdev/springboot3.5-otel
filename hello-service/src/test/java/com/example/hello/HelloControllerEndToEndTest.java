package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerEndToEndTest {

  private static final StubHttpServer userService =
      StubHttpServer.responding(
          "/api/users/1", "{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"}");

  private static final StubHttpServer greetingService =
      StubHttpServer.respondingWhenHeaderMatches(
          "/api/greetings",
          "Accept-Language",
          "zh",
          "{\"language\":\"zh\",\"message\":\"你好，世界！\"}");

  static {
    userService.start();
    greetingService.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("user.service.url", userService::baseUrl);
    registry.add("greeting.service.url", greetingService::baseUrl);
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @AfterAll
  static void shutdownServers() {
    userService.close();
    greetingService.close();
  }

  @Test
  void shouldNormalizeWeightedAcceptLanguageForDownstreamRequests() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

    var response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            HelloController.HelloResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().userId()).isEqualTo(1L);
    assertThat(response.getBody().userName()).isEqualTo("Alice");
    assertThat(response.getBody().greeting()).isEqualTo("你好，世界！");
    assertThat(response.getBody().language()).isEqualTo("zh");
    assertThat(greetingService.lastObservedHeader("Accept-Language")).isEqualTo("zh");
  }

  private static final class StubHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<String> acceptLanguage = new AtomicReference<>();

    private StubHttpServer(HttpServer server) {
      this.server = server;
    }

    static StubHttpServer responding(String path, String responseBody) {
      return create(
          path,
          exchange -> {
            writeJson(exchange, HttpStatus.OK.value(), responseBody);
          });
    }

    static StubHttpServer respondingWhenHeaderMatches(
        String path, String headerName, String expectedValue, String responseBody) {
      return create(
          path,
          exchange -> {
            String actualValue = exchange.getRequestHeaders().getFirst(headerName);
            if (!expectedValue.equals(actualValue)) {
              writeJson(
                  exchange,
                  HttpStatus.BAD_REQUEST.value(),
                  "{\"error\":\"unexpected header value\"}");
              return;
            }
            writeJson(exchange, HttpStatus.OK.value(), responseBody);
          });
    }

    private static StubHttpServer create(String path, ThrowingHandler handler) {
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        StubHttpServer stubServer = new StubHttpServer(server);
        server.createContext(
            path,
            exchange -> {
              stubServer.acceptLanguage.set(
                  exchange.getRequestHeaders().getFirst("Accept-Language"));
              handler.handle(exchange);
            });
        return stubServer;
      } catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }

    void start() {
      server.start();
    }

    String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    String lastObservedHeader(String headerName) {
      if (!"Accept-Language".equals(headerName)) {
        return null;
      }
      return acceptLanguage.get();
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void writeJson(HttpExchange exchange, int status, String responseBody)
        throws IOException {
      byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(body);
      }
    }
  }

  @FunctionalInterface
  private interface ThrowingHandler {
    void handle(HttpExchange exchange) throws IOException;
  }
}

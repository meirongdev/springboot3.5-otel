package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end test using contract stubs for downstream services.
 *
 * <p>StubRunner resolves the stub ports at runtime and injects them via
 * ${stubrunner.runningstubs.<artifactId>.port} placeholders.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "user.service.url=http://localhost:${stubrunner.runningstubs.user-service.port:8081}",
      "greeting.service.url=http://localhost:${stubrunner.runningstubs.greeting-service.port:8082}"
    })
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    ids = {"com.example:greeting-service:+:stubs", "com.example:user-service:+:stubs"},
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH)
class HelloControllerEndToEndTest {

  @MockitoBean
  private org.springframework.kafka.core.KafkaTemplate<
          String, com.example.shared.kafka.event.GreetingRequestedEvent>
      kafkaTemplate;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldNormalizeWeightedAcceptLanguageForDownstreamRequests() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

    var response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            HelloResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().userId()).isEqualTo(1L);
    assertThat(response.getBody().userName()).isEqualTo("Alice");
    assertThat(response.getBody().greeting()).isEqualTo("你好，世界！");
    assertThat(response.getBody().language()).isEqualTo("zh");
  }

  @Test
  void shouldReturnEnglishGreetingForEnglishRequest() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept-Language", "en-US,en;q=0.9");

    var response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            HelloResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().greeting()).isEqualTo("Hello, World!");
    assertThat(response.getBody().language()).isEqualTo("en");
  }
}

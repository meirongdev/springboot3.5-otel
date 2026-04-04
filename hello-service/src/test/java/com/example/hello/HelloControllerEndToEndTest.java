package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerPort;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureStubRunner(
    ids = {"com.example:greeting-service:+:stubs", "com.example:user-service:+:stubs"},
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH)
class HelloControllerEndToEndTest {

  @StubRunnerPort("user-service")
  int userServicePort;

  @StubRunnerPort("greeting-service")
  int greetingServicePort;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @SpyBean private GreetingServiceClient greetingServiceClient;
  @SpyBean private UserServiceClient userServiceClient;

  @org.junit.jupiter.api.BeforeEach
  void setup() {
    // Intercept and redirect to the correct stub port
    doAnswer(
            invocation -> {
              String lang = invocation.getArgument(0);
              GreetingServiceClient clientWithCorrectPort =
                  new GreetingServiceClient(
                      org.springframework.web.client.RestClient.builder().build(),
                      "http://localhost:" + greetingServicePort);
              return clientWithCorrectPort.getGreeting(lang);
            })
        .when(greetingServiceClient)
        .getGreeting(org.mockito.ArgumentMatchers.anyString());

    doAnswer(
            invocation -> {
              Long id = invocation.getArgument(0);
              UserServiceClient clientWithCorrectPort =
                  new UserServiceClient(
                      org.springframework.web.client.RestClient.builder().build(),
                      "http://localhost:" + userServicePort);
              return clientWithCorrectPort.getUser(id);
            })
        .when(userServiceClient)
        .getUser(org.mockito.ArgumentMatchers.anyLong());
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
  }
}

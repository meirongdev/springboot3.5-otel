package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerPort;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(
    ids = "com.example:greeting-service:+:stubs",
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH)
class GreetingServiceContractTest {

  @StubRunnerPort("greeting-service")
  int port;

  private GreetingServiceClient greetingServiceClient;

  @BeforeEach
  void setup() {
    greetingServiceClient =
        new GreetingServiceClient(RestClient.builder().build(), "http://localhost:" + port);
  }

  @Test
  void shouldGetGreetingFromStub() {
    var greeting = greetingServiceClient.getGreeting("en");

    assertThat(greeting.language()).isEqualTo("en");
    assertThat(greeting.message()).isEqualTo("Hello, World!");
  }
}

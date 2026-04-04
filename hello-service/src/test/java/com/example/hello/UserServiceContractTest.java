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
    ids = "com.example:user-service:+:stubs",
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH)
class UserServiceContractTest {

  @StubRunnerPort("user-service")
  int port;

  private UserServiceClient userServiceClient;

  @BeforeEach
  void setup() {
    userServiceClient =
        new UserServiceClient(RestClient.builder().build(), "http://localhost:" + port);
  }

  @Test
  void shouldGetUserFromStub() {
    var user = userServiceClient.getUser(1L);

    assertThat(user.id()).isEqualTo(1L);
    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.email()).isEqualTo("alice@example.com");
  }
}

package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class HelloServiceIntegrationTest {

  @Autowired private HelloService helloService;

  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private GreetingServiceClient greetingServiceClient;

  @SuppressWarnings("UnusedVariable")
  @MockitoBean
  private KafkaEventPublisher kafkaEventPublisher;

  @SuppressWarnings("UnusedVariable")
  @MockitoBean
  private io.micrometer.tracing.Tracer tracer;

  @Test
  void shouldLoadContextAndOrchestrate() {
    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = helloService.getHello(1L, "en");

    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
  }
}

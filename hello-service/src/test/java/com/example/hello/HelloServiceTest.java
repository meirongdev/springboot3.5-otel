package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelloServiceTest {

  @Mock private UserServiceClient userServiceClient;

  @Mock private GreetingServiceClient greetingServiceClient;

  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @InjectMocks private HelloService helloService;

  @Test
  void shouldOrchestrateCalls() {
    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = helloService.getHello(1L, "en");

    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
    assertThat(result.language()).isEqualTo("en");
  }

  @Test
  void shouldForwardLanguageToGreetingClient() {
    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("zh"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("zh", "你好，世界！"));

    var result = helloService.getHello(1L, "zh");

    assertThat(result.greeting()).isEqualTo("你好，世界！");
    assertThat(result.language()).isEqualTo("zh");
  }
}

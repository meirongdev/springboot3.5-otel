package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class HelloServiceTest {

  @Mock private UserServiceClient userServiceClient;

  @Mock private GreetingServiceClient greetingServiceClient;

  @Mock private KafkaEventPublisher kafkaEventPublisher;

  /** Synchronous executor for unit tests -- runs tasks immediately on the calling thread. */
  private final TaskExecutor directExecutor = (Runnable task) -> task.run();

  @Test
  void shouldOrchestrateCalls() {
    HelloService service =
        new HelloService(
            userServiceClient, greetingServiceClient, kafkaEventPublisher, directExecutor);

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = service.getHello(1L, "en");

    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
    assertThat(result.language()).isEqualTo("en");
  }

  @Test
  void shouldForwardLanguageToGreetingClient() {
    HelloService service =
        new HelloService(
            userServiceClient, greetingServiceClient, kafkaEventPublisher, directExecutor);

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("zh"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("zh", "你好，世界！"));

    var result = service.getHello(1L, "zh");

    assertThat(result.greeting()).isEqualTo("你好，世界！");
    assertThat(result.language()).isEqualTo("zh");
  }
}

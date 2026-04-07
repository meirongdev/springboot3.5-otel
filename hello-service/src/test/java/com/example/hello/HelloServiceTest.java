package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
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
  @Mock private Tracer tracer;

  private final TaskExecutor directExecutor = Runnable::run;
  // ObservationRegistry.NOOP returns null from getCurrentObservation() — matches the
  // null-guard in HelloService so no NPE occurs in unit tests.
  private final ObservationRegistry registry = ObservationRegistry.NOOP;

  private HelloService service() {
    return new HelloService(
        userServiceClient,
        greetingServiceClient,
        kafkaEventPublisher,
        directExecutor,
        tracer,
        registry);
  }

  @Test
  void shouldOrchestrateCalls() {
    var span = mock(Span.class);
    var ctx = mock(TraceContext.class);
    when(tracer.currentSpan()).thenReturn(span);
    when(span.context()).thenReturn(ctx);
    when(ctx.traceId()).thenReturn("abc123");

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = service().getHello(1L, "en");

    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
    assertThat(result.language()).isEqualTo("en");
  }

  @Test
  void shouldForwardLanguageToGreetingClient() {
    when(tracer.currentSpan()).thenReturn(null); // no active span is valid

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("zh"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("zh", "你好，世界！"));

    var result = service().getHello(1L, "zh");

    assertThat(result.greeting()).isEqualTo("你好，世界！");
    assertThat(result.language()).isEqualTo("zh");
  }
}

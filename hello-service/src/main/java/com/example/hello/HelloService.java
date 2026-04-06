package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;
  private final KafkaEventPublisher kafkaEventPublisher;
  private final TaskExecutor virtualTaskExecutor;

  public HelloService(
      UserServiceClient userServiceClient,
      GreetingServiceClient greetingServiceClient,
      KafkaEventPublisher kafkaEventPublisher,
      TaskExecutor virtualTaskExecutor) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
    this.kafkaEventPublisher = kafkaEventPublisher;
    this.virtualTaskExecutor = virtualTaskExecutor;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    // Use parallel execution with explicit executor for OTel context propagation.
    // The virtualTaskExecutor is backed by Java 25 virtual threads and is
    // automatically wrapped by Spring's ApplicationTaskExecutor which propagates
    // the Micrometer Observation context (traceId, spanId, baggage) to child threads.
    //
    // WARNING: Do NOT use CompletableFuture.supplyAsync(supplier) without an executor --
    // it falls back to the common ForkJoinPool, which breaks OTel context propagation
    // and uses platform threads instead of virtual threads.
    var userFuture =
        CompletableFuture.supplyAsync(() -> userServiceClient.getUser(userId), virtualTaskExecutor);
    var greetingFuture =
        CompletableFuture.supplyAsync(
            () -> greetingServiceClient.getGreeting(acceptLanguage), virtualTaskExecutor);

    // Wait for both to complete
    CompletableFuture.allOf(userFuture, greetingFuture).join();

    UserServiceClient.UserDTO user = userFuture.join();
    GreetingServiceClient.GreetingDTO greeting = greetingFuture.join();

    // Publish event to Kafka for async processing (fire-and-forget via @Async)
    kafkaEventPublisher.publishGreetingRequested(
        "greeting-events",
        new GreetingRequestedEvent(
            null, userId, greeting.language(), greeting.message(), Instant.now()));

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }
}

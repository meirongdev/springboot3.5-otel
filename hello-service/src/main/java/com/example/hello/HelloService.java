package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private static final Logger log = LoggerFactory.getLogger(HelloService.class);
  private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;
  private final KafkaEventPublisher kafkaEventPublisher;
  private final TaskExecutor taskExecutor;
  private final Tracer tracer;

  public HelloService(
      UserServiceClient userServiceClient,
      GreetingServiceClient greetingServiceClient,
      KafkaEventPublisher kafkaEventPublisher,
      TaskExecutor taskExecutor,
      Tracer tracer) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
    this.kafkaEventPublisher = kafkaEventPublisher;
    this.taskExecutor = taskExecutor;
    this.tracer = tracer;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    // Use parallel execution with explicit executor for OTel context propagation.
    // WARNING: Do NOT use CompletableFuture.supplyAsync(supplier) without an executor —
    // it falls back to the common ForkJoinPool, which breaks OTel context propagation.
    var userFuture =
        CompletableFuture.supplyAsync(() -> userServiceClient.getUser(userId), taskExecutor);
    var greetingFuture =
        CompletableFuture.supplyAsync(
            () -> greetingServiceClient.getGreeting(acceptLanguage), taskExecutor);

    CompletableFuture.allOf(userFuture, greetingFuture).join();

    UserServiceClient.UserDTO user = userFuture.join();
    GreetingServiceClient.GreetingDTO greeting = greetingFuture.join();

    log.info(
        AUDIT,
        "Greeting requested userId={} language={} user={}",
        userId,
        greeting.language(),
        user.name());

    kafkaEventPublisher.publishGreetingRequested(
        "greeting-events",
        new GreetingRequestedEvent(
            currentTraceId(), userId, greeting.language(), greeting.message(), Instant.now()));

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }

  private String currentTraceId() {
    var span = tracer.currentSpan();
    return span != null ? span.context().traceId() : null;
  }
}

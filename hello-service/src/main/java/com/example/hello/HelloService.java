package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;
  private final KafkaEventPublisher kafkaEventPublisher;

  public HelloService(
      UserServiceClient userServiceClient,
      GreetingServiceClient greetingServiceClient,
      KafkaEventPublisher kafkaEventPublisher) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    UserServiceClient.UserDTO user = userServiceClient.getUser(userId);
    GreetingServiceClient.GreetingDTO greeting = greetingServiceClient.getGreeting(acceptLanguage);

    // Publish event to Kafka for async processing (fire-and-forget via @Async)
    kafkaEventPublisher.publishGreetingRequested(
        "greeting-events",
        new GreetingRequestedEvent(
            null, userId, greeting.language(), greeting.message(), Instant.now()));

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }
}

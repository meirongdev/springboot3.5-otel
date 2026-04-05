package com.example.user;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes greeting events from Kafka for async processing and observability demo. */
@Component
public class GreetingEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(GreetingEventConsumer.class);

  @KafkaListener(topics = "greeting-events", groupId = "user-service-group")
  @Observed(name = "kafka.event.consume", contextualName = "kafka.consume")
  public void handleGreetingRequested(GreetingRequestedEvent event) {
    log.info(
        "Received greeting event: userId={}, language={}, greeting={}",
        event.userId(),
        event.language(),
        event.greeting());
  }
}

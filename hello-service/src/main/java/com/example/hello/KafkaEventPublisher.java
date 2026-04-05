package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Publishes greeting events to Kafka for async processing. */
@Component
public class KafkaEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Async
  @Observed(name = "kafka.event.publish", contextualName = "kafka.publish")
  public void publishGreetingRequested(String topic, GreetingRequestedEvent event) {
    try {
      kafkaTemplate
          .send(topic, event.userId().toString(), event)
          .get(5, java.util.concurrent.TimeUnit.SECONDS);
      log.debug("Published greeting event for userId={}", event.userId());
    } catch (Exception e) {
      log.warn("Failed to publish greeting event: {}", e.getMessage());
    }
  }
}

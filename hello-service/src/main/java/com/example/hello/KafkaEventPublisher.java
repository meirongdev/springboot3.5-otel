package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
  private final Counter kafkaPublishFailureCounter;

  public KafkaEventPublisher(
      KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaPublishFailureCounter =
        Counter.builder("kafka.publish.failure.count")
            .description("Number of failed Kafka publish attempts")
            .register(meterRegistry);
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
      kafkaPublishFailureCounter.increment();
      log.warn(
          "Failed to publish greeting event for userId={}: {}", event.userId(), e.getMessage(), e);
      throw new RuntimeException("Failed to publish greeting event", e);
    }
  }
}

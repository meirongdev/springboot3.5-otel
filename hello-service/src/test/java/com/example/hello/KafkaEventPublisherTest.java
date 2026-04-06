package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

  @Mock private KafkaTemplate<String, GreetingRequestedEvent> kafkaTemplate;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    kafkaEventPublisher = new KafkaEventPublisher(kafkaTemplate, meterRegistry);
  }

  @Test
  void shouldPublishEventToKafka() {
    var event = new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    var recordMetadata = mock(RecordMetadata.class);
    var producerRecord =
        new ProducerRecord<String, GreetingRequestedEvent>("greeting-events", "1", event);
    var sendResult = new SendResult<>(producerRecord, recordMetadata);
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), eq(event)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    kafkaEventPublisher.publishGreetingRequested("greeting-events", event);

    verify(kafkaTemplate).send(eq("greeting-events"), eq("1"), eq(event));
  }

  @Test
  void shouldIncrementFailureCounterOnPublishFailure() {
    var event = new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), eq(event)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

    // Fire-and-forget: no exception thrown to caller; failure is logged and metered
    kafkaEventPublisher.publishGreetingRequested("greeting-events", event);

    assertThat(meterRegistry.counter("kafka.publish.failure.count").count()).isEqualTo(1.0);
  }
}

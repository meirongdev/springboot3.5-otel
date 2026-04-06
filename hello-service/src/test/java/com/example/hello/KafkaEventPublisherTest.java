package com.example.hello;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaEventPublisherTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    kafkaEventPublisher = new KafkaEventPublisher(kafkaTemplate, meterRegistry);
  }

  @Test
  void shouldPublishEventToKafka() throws Exception {
    // given
    var event = new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    var recordMetadata = mock(RecordMetadata.class);
    var producerRecord = new ProducerRecord<String, Object>("greeting-events", "1", event);
    var sendResult = new SendResult<String, Object>(producerRecord, recordMetadata);
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), eq(event)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // when
    kafkaEventPublisher.publishGreetingRequested("greeting-events", event);

    // then
    verify(kafkaTemplate).send(eq("greeting-events"), eq("1"), eq(event));
  }

  @Test
  void shouldHandlePublishFailureGracefully() {
    // given
    var event = new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), eq(event)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

    // when/then - exception is thrown (will be handled by AsyncUncaughtExceptionHandler in
    // production)
    assertThatThrownBy(() -> kafkaEventPublisher.publishGreetingRequested("greeting-events", event))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to publish greeting event");
  }
}

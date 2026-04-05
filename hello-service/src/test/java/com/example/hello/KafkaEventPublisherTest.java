package com.example.hello;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private KafkaEventPublisher kafkaEventPublisher;

  @Test
  void shouldPublishEventToKafka() throws Exception {
    // given
    var event =
        new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    var recordMetadata = mock(RecordMetadata.class);
    var producerRecord = new ProducerRecord<String, Object>("greeting-events", "1", event);
    var sendResult = new SendResult<String, Object>(producerRecord, recordMetadata);
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), any()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // when
    kafkaEventPublisher.publishGreetingRequested("greeting-events", event);

    // Wait for async completion
    Thread.sleep(100);

    // then
    verify(kafkaTemplate).send(eq("greeting-events"), eq("1"), any());
  }

  @Test
  void shouldHandlePublishFailureGracefully() throws Exception {
    // given
    var event =
        new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());
    when(kafkaTemplate.send(eq("greeting-events"), eq("1"), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

    // when
    kafkaEventPublisher.publishGreetingRequested("greeting-events", event);

    // Wait for async completion
    Thread.sleep(100);

    // then - no exception thrown, failure handled gracefully
    verify(kafkaTemplate).send(eq("greeting-events"), eq("1"), any());
  }
}

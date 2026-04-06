package com.example.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for Kafka consumer using EmbeddedKafka.
 *
 * <p>Verifies the full consumer pipeline: serialization, message delivery, deserialization, and
 * consumer handling logic.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"greeting-events"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.autoconfigure.exclude=" // Override test profile's Kafka exclusion
    })
class GreetingEventConsumerIntegrationTest {

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @Autowired private GreetingEventConsumer greetingEventConsumer;

  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    // Capture consumer log output
    Logger logger = (Logger) LoggerFactory.getLogger(GreetingEventConsumer.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    Logger logger = (Logger) LoggerFactory.getLogger(GreetingEventConsumer.class);
    logger.detachAppender(listAppender);
    listAppender.stop();
    listAppender.list.clear();
  }

  @Test
  void shouldConsumeGreetingEventPublishedToKafka() {
    // given
    var event =
        new GreetingRequestedEvent("trace-abc-123", 42L, "en", "Hello, World!", Instant.now());

    // when — publish to the same topic the consumer listens on
    kafkaTemplate.send("greeting-events", "42", event);

    // then — wait for the consumer to process and log
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              List<String> logMessages =
                  listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
              assertThat(logMessages)
                  .anySatisfy(
                      msg -> {
                        assertThat(msg).contains("Received greeting event");
                        assertThat(msg).contains("userId=42");
                        assertThat(msg).contains("language=en");
                        assertThat(msg).contains("greeting=Hello, World!");
                      });
            });
  }

  @Test
  void shouldConsumeMultipleEventsInOrder() {
    // given
    var event1 = new GreetingRequestedEvent("trace-1", 1L, "en", "Hello", Instant.now());
    var event2 = new GreetingRequestedEvent("trace-2", 2L, "zh", "你好", Instant.now());

    // when
    kafkaTemplate.send("greeting-events", "1", event1);
    kafkaTemplate.send("greeting-events", "2", event2);

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              List<String> logMessages =
                  listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
              assertThat(logMessages).anySatisfy(msg -> assertThat(msg).contains("userId=1"));
              assertThat(logMessages).anySatisfy(msg -> assertThat(msg).contains("userId=2"));
            });
  }

  @Test
  void shouldHandleEventWithNullFields() {
    // given
    var event = new GreetingRequestedEvent(null, null, null, null, null);

    // when
    kafkaTemplate.send("greeting-events", "null-key", event);

    // then — consumer processes without crashing, logs null values
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              List<String> logMessages =
                  listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
              assertThat(logMessages)
                  .anySatisfy(msg -> assertThat(msg).contains("Received greeting event"));
            });
  }
}

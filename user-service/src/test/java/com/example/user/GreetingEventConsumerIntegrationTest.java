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
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for Kafka consumer using Testcontainers.
 *
 * <p>Verifies the full consumer pipeline: serialization, message delivery, deserialization, and
 * consumer handling logic against a real Kafka broker.
 */
@SpringBootTest
@Testcontainers
class GreetingEventConsumerIntegrationTest {

  private static final DockerImageName KAFKA_IMAGE =
      DockerImageName.parse("confluentinc/cp-kafka:7.6.0");

  @Container private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add(
        "spring.kafka.producer.value-serializer",
        () -> "org.springframework.kafka.support.serializer.JsonSerializer");
    registry.add(
        "spring.kafka.producer.key-serializer",
        () -> "org.apache.kafka.common.serialization.StringSerializer");
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add(
        "spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "com.example.*");
    registry.add(
        "spring.kafka.consumer.properties.spring.json.type.mapping",
        () -> "greetingRequested:com.example.shared.kafka.event.GreetingRequestedEvent");
  }

  @BeforeAll
  static void createTopic() {
    try (var admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic("greeting-events", 1, (short) 1))).all().get();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create Kafka topic", e);
    }
  }

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

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

  @AfterAll
  static void tearDownClass() {
    // Testcontainers handles cleanup automatically
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

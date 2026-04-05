package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"greeting-events"},
    brokerProperties = {"auto.create.topics.enable=true"})
@ActiveProfiles("test")
class KafkaIntegrationTest {

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  private KafkaConsumer<String, GreetingRequestedEvent> consumer;

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.close();
    }
  }

  @Test
  void shouldPublishAndConsumeEvent() {
    // given
    var event =
        new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());

    String brokerAddress = embeddedKafka.getBrokersAsString();

    // Create a consumer to verify the event was published
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                brokerAddress,
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class.getName(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                JsonDeserializer.TRUSTED_PACKAGES,
                "com.example.*",
                JsonDeserializer.TYPE_MAPPINGS,
                "greetingRequested:com.example.shared.kafka.event.GreetingRequestedEvent"));

    consumer.subscribe(java.util.List.of("greeting-events"));

    // when
    kafkaTemplate.send("greeting-events", event.userId().toString(), event);

    // then - wait for event to be available
    var records = consumer.poll(Duration.ofSeconds(5));
    assertThat(records).isNotEmpty();

    ConsumerRecord<String, GreetingRequestedEvent> record = records.iterator().next();
    assertThat(record.key()).isEqualTo("1");
    assertThat(record.value()).isEqualTo(event);
  }
}

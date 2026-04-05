package com.example.hello;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Kafka topic configuration. */
@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic greetingEventsTopic() {
    return TopicBuilder.name("greeting-events").partitions(1).replicas(1).build();
  }
}

# Kafka + OpenTelemetry Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Kafka producer/consumer to demonstrate OTel Kafka instrumentation and sync+async pattern coexistence.

**Architecture:** hello-service publishes `GreetingRequestedEvent` to Kafka after HTTP orchestration; user-service consumes the event. Both spans appear in same OTel trace via context propagation in Kafka headers.

**Tech Stack:** Spring Boot 3.5, Spring Kafka, OpenTelemetry, Java 25, Docker Compose (Confluent Kafka 7.6.0)

---

### Task 1: Add Spring Kafka Dependency to Shared Module

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Add spring-kafka dependency**

Add `spring-kafka` as an `api` dependency so all services can use it:

```kotlin
// In dependencies block, after the logback appender line:
api("org.springframework.kafka:spring-kafka")
```

The updated dependencies section should look like:
```kotlin
dependencies {
    // Spring Boot starters
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-actuator")

    // Micrometer Tracing + OpenTelemetry Bridge
    api("io.micrometer:micrometer-tracing")
    api("io.micrometer:micrometer-tracing-bridge-otel")

    // OpenTelemetry SDK + OTLP Exporter
    api("io.opentelemetry:opentelemetry-api")
    api("io.opentelemetry:opentelemetry-exporter-otlp")

    // Micrometer OTLP Registry for metrics
    api("io.micrometer:micrometer-registry-otlp")

    // OpenTelemetry Logback Appender (from instrumentation project)
    api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.13.0-alpha")

    // Spring Kafka for Kafka producer/consumer support
    api("org.springframework.kafka:spring-kafka")

    // Spring Boot autoconfigure for OTel
    implementation("org.springframework.boot:spring-boot-starter-aop")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

- [ ] **Step 2: Verify dependency resolution**

Run: `./gradlew :shared:dependencies --configuration compileClasspath | grep kafka`
Expected: Should show `spring-kafka` in the dependency tree

- [ ] **Step 3: Commit**

```bash
git add shared/build.gradle.kts
git commit -m "feat: add spring-kafka dependency to shared module"
```

---

### Task 2: Create Shared Kafka Configuration and Event DTO

**Files:**
- Create: `shared/src/main/resources/application-kafka.yaml`
- Create: `shared/src/main/java/com/example/shared/kafka/event/GreetingRequestedEvent.java`

- [ ] **Step 1: Create application-kafka.yaml**

Create `shared/src/main/resources/application-kafka.yaml`:

```yaml
# Shared Kafka configuration for all services
# Import via: spring.config.import: optional:classpath:application-kafka.yaml

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.*"
        spring.json.type.mapping: "greetingRequested:com.example.shared.kafka.event.GreetingRequestedEvent"
```

- [ ] **Step 2: Create GreetingRequestedEvent DTO**

Create `shared/src/main/java/com/example/shared/kafka/event/GreetingRequestedEvent.java`:

```java
package com.example.shared.kafka.event;

import java.time.Instant;

/**
 * Event published when a greeting is requested.
 * Used to demonstrate Kafka + OTel trace context propagation.
 */
public record GreetingRequestedEvent(
    String traceId,
    Long userId,
    String language,
    String greeting,
    Instant timestamp
) {}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :shared:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/main/resources/application-kafka.yaml shared/src/main/java/com/example/shared/kafka/event/GreetingRequestedEvent.java
git commit -m "feat: add shared Kafka config and GreetingRequestedEvent DTO"
```

---

### Task 3: Add Kafka Service to Docker Compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Add Kafka service to compose.yaml**

Add the Kafka service before the `volumes:` section at the end of compose.yaml. Insert it after the `greeting-service` block:

```yaml
  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://localhost:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9093", "--list"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
```

- [ ] **Step 2: Add Kafka dependency to hello-service**

Find the `hello-service` block's `depends_on:` section and add kafka:

Change from:
```yaml
    depends_on:
      otel-collector:
        condition: service_started
      user-service:
        condition: service_started
      greeting-service:
        condition: service_started
```

To:
```yaml
    depends_on:
      otel-collector:
        condition: service_started
      user-service:
        condition: service_started
      greeting-service:
        condition: service_started
      kafka:
        condition: service_healthy
```

- [ ] **Step 3: Add Kafka dependency to user-service**

Find the `user-service` block's `depends_on:` section and add kafka:

Change from:
```yaml
    depends_on:
      otel-collector:
        condition: service_started
```

To:
```yaml
    depends_on:
      otel-collector:
        condition: service_started
      kafka:
        condition: service_healthy
```

- [ ] **Step 4: Add KAFKA_BOOTSTRAP_SERVERS env var to hello-service**

Add to hello-service's environment section (after the existing env vars):
```yaml
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

- [ ] **Step 5: Add KAFKA_BOOTSTRAP_SERVERS env var to user-service**

Add to user-service's environment section:
```yaml
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

- [ ] **Step 6: Verify compose syntax**

Run: `docker compose config`
Expected: Valid YAML output with no errors

- [ ] **Step 7: Commit**

```bash
git add compose.yaml
git commit -m "feat: add Kafka service to Docker Compose"
```

---

### Task 4: Configure hello-service as Kafka Producer

**Files:**
- Create: `hello-service/src/main/java/com/example/hello/KafkaEventPublisher.java`
- Create: `hello-service/src/main/java/com/example/hello/KafkaTopicConfig.java`
- Modify: `hello-service/src/main/java/com/example/hello/HelloService.java`
- Modify: `hello-service/src/main/resources/application.yaml`
- Modify: `hello-service/build.gradle.kts`

- [ ] **Step 1: Update hello-service application.yaml to import Kafka config**

Change the `spring.config.import` line in `hello-service/src/main/resources/application.yaml` from:
```yaml
spring:
  application:
    name: hello-service
  config:
    import: "optional:classpath:application-otel.yaml"
```

To:
```yaml
spring:
  application:
    name: hello-service
  config:
    import:
      - "optional:classpath:application-otel.yaml"
      - "optional:classpath:application-kafka.yaml"
```

- [ ] **Step 2: Create KafkaEventPublisher**

Create `hello-service/src/main/java/com/example/hello/KafkaEventPublisher.java`:

```java
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
      kafkaTemplate.send(topic, event.userId().toString(), event).get(5, java.util.concurrent.TimeUnit.SECONDS);
      log.debug("Published greeting event for userId={}", event.userId());
    } catch (Exception e) {
      log.warn("Failed to publish greeting event: {}", e.getMessage());
    }
  }
}
```

- [ ] **Step 3: Create KafkaTopicConfig**

Create `hello-service/src/main/java/com/example/hello/KafkaTopicConfig.java`:

```java
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
```

- [ ] **Step 4: Update HelloService to inject and use KafkaEventPublisher**

Update `hello-service/src/main/java/com/example/hello/HelloService.java`:

```java
package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private static final Logger log = LoggerFactory.getLogger(HelloService.class);

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;
  private final KafkaEventPublisher kafkaEventPublisher;

  public HelloService(
      UserServiceClient userServiceClient,
      GreetingServiceClient greetingServiceClient,
      KafkaEventPublisher kafkaEventPublisher) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    UserServiceClient.UserDTO user = userServiceClient.getUser(userId);
    GreetingServiceClient.GreetingDTO greeting = greetingServiceClient.getGreeting(acceptLanguage);

    // Publish event to Kafka for async processing (fire-and-forget via @Async)
    kafkaEventPublisher.publishGreetingRequested(
        "greeting-events",
        new GreetingRequestedEvent(null, userId, greeting.language(), greeting.message(), Instant.now()));

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }
}
```

- [ ] **Step 5: Enable async support in hello-service**

Add to `hello-service/src/main/java/com/example/hello/HelloApplication.java` (or create a config class):

Check if `@EnableAsync` already exists. If not, add it to the main application class:

```java
package com.example.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HelloApplication {
  public static void main(String[] args) {
    SpringApplication.run(HelloApplication.class, args);
  }
}
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :hello-service:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add hello-service/src/main/java/com/example/hello/KafkaEventPublisher.java hello-service/src/main/java/com/example/hello/KafkaTopicConfig.java hello-service/src/main/java/com/example/hello/HelloService.java hello-service/src/main/java/com/example/hello/HelloApplication.java hello-service/src/main/resources/application.yaml
git commit -m "feat: add Kafka producer to hello-service"
```

---

### Task 5: Configure user-service as Kafka Consumer

**Files:**
- Create: `user-service/src/main/java/com/example/user/GreetingEventConsumer.java`
- Modify: `user-service/src/main/resources/application.yaml`
- Modify: `user-service/build.gradle.kts`

- [ ] **Step 1: Update user-service application.yaml to import Kafka config**

Change the `spring.config.import` line in `user-service/src/main/resources/application.yaml` from:
```yaml
spring:
  application:
    name: user-service
  config:
    import: "optional:classpath:application-otel.yaml"
```

To:
```yaml
spring:
  application:
    name: user-service
  config:
    import:
      - "optional:classpath:application-otel.yaml"
      - "optional:classpath:application-kafka.yaml"
```

- [ ] **Step 2: Create GreetingEventConsumer**

Create `user-service/src/main/java/com/example/user/GreetingEventConsumer.java`:

```java
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
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :user-service:build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add user-service/src/main/java/com/example/user/GreetingEventConsumer.java user-service/src/main/resources/application.yaml
git commit -m "feat: add Kafka consumer to user-service"
```

---

### Task 6: Write Unit Tests for Kafka Components

**Files:**
- Create: `hello-service/src/test/java/com/example/hello/KafkaEventPublisherTest.java`
- Create: `user-service/src/test/java/com/example/user/GreetingEventConsumerTest.java`

- [ ] **Step 1: Create KafkaEventPublisherTest**

Create `hello-service/src/test/java/com/example/hello/KafkaEventPublisherTest.java`:

```java
package com.example.hello;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
    var topicPartition = new TopicPartition("greeting-events", 0);
    var recordMetadata = mock(RecordMetadata.class);
    when(recordMetadata.topic()).thenReturn("greeting-events");
    when(recordMetadata.partition()).thenReturn(0);
    when(recordMetadata.offset()).thenReturn(0L);
    var sendResult = new SendResult<String, Object>(event, recordMetadata);
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
```

- [ ] **Step 2: Create GreetingEventConsumerTest**

Create `user-service/src/test/java/com/example/user/GreetingEventConsumerTest.java`:

```java
package com.example.user;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GreetingEventConsumerTest {

  private GreetingEventConsumer consumer;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    consumer = new GreetingEventConsumer();

    // Capture log output
    Logger logger = (Logger) LoggerFactory.getLogger(GreetingEventConsumer.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @Test
  void shouldLogReceivedEvent() {
    // given
    var event =
        new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());

    // when
    consumer.handleGreetingRequested(event);

    // then
    assertThat(listAppender.list).isNotEmpty();
    String loggedMessage = listAppender.list.get(0).getFormattedMessage();
    assertThat(loggedMessage).contains("Received greeting event");
    assertThat(loggedMessage).contains("userId=1");
    assertThat(loggedMessage).contains("language=en");
    assertThat(loggedMessage).contains("greeting=Hello");
  }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :hello-service:test --tests KafkaEventPublisherTest && ./gradlew :user-service:test --tests GreetingEventConsumerTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add hello-service/src/test/java/com/example/hello/KafkaEventPublisherTest.java user-service/src/test/java/com/example/user/GreetingEventConsumerTest.java
git commit -m "test: add unit tests for Kafka components"
```

---

### Task 7: Add Kafka Integration Test with EmbeddedKafka

**Files:**
- Create: `hello-service/src/test/java/com/example/hello/KafkaIntegrationTest.java`
- Modify: `hello-service/build.gradle.kts`
- Create: `hello-service/src/test/resources/application-test.yaml`

- [ ] **Step 1: Add test dependencies to hello-service**

Add to `hello-service/build.gradle.kts` dependencies:
```kotlin
testImplementation("org.springframework.kafka:spring-kafka-test")
```

- [ ] **Step 2: Create test application config**

Create `hello-service/src/test/resources/application-test.yaml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

- [ ] **Step 3: Create KafkaIntegrationTest**

Create `hello-service/src/test/java/com/example/hello/KafkaIntegrationTest.java`:

```java
package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
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

    // Create a consumer to verify the event was published
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092",
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
```

- [ ] **Step 4: Run integration test**

Run: `./gradlew :hello-service:test --tests KafkaIntegrationTest`
Expected: Test passes

- [ ] **Step 5: Commit**

```bash
git add hello-service/build.gradle.kts hello-service/src/test/resources/application-test.yaml hello-service/src/test/java/com/example/hello/KafkaIntegrationTest.java
git commit -m "test: add Kafka integration test with EmbeddedKafka"
```

---

### Task 8: Add Kafka Architecture Tests

**Files:**
- Modify: `arch-tests/src/test/java/com/example/ArchitectureRulesTest.java`

- [ ] **Step 1: Add Kafka architecture rule to ArchitectureRulesTest**

Add this rule to `arch-tests/src/test/java/com/example/ArchitectureRulesTest.java` (before the closing brace of the class):

```java
  // Kafka events must be records for immutability
  @ArchTest
  static final ArchRule kafkaEventsMustBeRecords =
      classes()
          .that()
          .resideInAPackage("..kafka.event..")
          .should()
          .beRecords()
          .because("Kafka event types should be immutable records");
```

The full file should now have the new rule added at the end:

```java
package com.example;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule sharedModuleIndependence =
      noClasses()
          .that()
          .resideInAPackage("com.example.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.hello..", "com.example.user..", "com.example.greeting..");

  @ArchTest
  static final ArchRule controllerDoesNotAccessRepository =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule noCycles = slices().matching("com.example.(*)..").should().beFreeOfCycles();

  // Java 25 best practice: DTO / Response / Request types must be records (immutable by design)
  @ArchTest
  static final ArchRule dtosMustBeRecords =
      classes()
          .that()
          .haveSimpleNameEndingWith("DTO")
          .or()
          .haveSimpleNameEndingWith("Response")
          .or()
          .haveSimpleNameEndingWith("Request")
          .should()
          .beRecords()
          .because("Java 25 best practice: data-carrier types should be immutable records");

  // Spring Boot 3.5 best practice: no manual construction of OTel SDK providers —
  // rely on Spring Boot auto-configuration instead.
  @ArchTest
  static final ArchRule noManualOtelSdkConstruction =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("io.opentelemetry.sdk.logs.SdkLoggerProvider")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("io.opentelemetry.sdk.trace.SdkTracerProvider")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("io.opentelemetry.sdk.metrics.SdkMeterProvider")
          .because(
              "Spring Boot 3.5 auto-configures the OTel SDK; manual construction bypasses"
                  + " auto-configuration and creates duplicate/conflicting providers");

  // Kafka events must be records for immutability
  @ArchTest
  static final ArchRule kafkaEventsMustBeRecords =
      classes()
          .that()
          .resideInAPackage("..kafka.event..")
          .should()
          .beRecords()
          .because("Kafka event types should be immutable records");
}
```

- [ ] **Step 2: Run architecture tests**

Run: `./gradlew :arch-tests:test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add arch-tests/src/test/java/com/example/ArchitectureRulesTest.java
git commit -m "test: add Kafka events architecture rule"
```

---

### Task 9: Run Full Build and Verify All Tests

**Files:**
- None (verification task)

- [ ] **Step 1: Run full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Run spotless check**

Run: `./gradlew spotlessCheck`
Expected: No formatting issues

If there are issues, fix them:
```bash
./gradlew spotlessApply
```

- [ ] **Step 3: Run coverage report**

Run: `./gradlew testCodeCoverageReport`
Expected: Coverage ≥ 60%

- [ ] **Step 4: Commit if formatting changes**

```bash
git add .
git commit -m "style: apply spotless formatting"
```

---

### Task 10: Update README with Kafka Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add Kafka section to README**

Find the architecture diagram section and update it. Look for the current diagram showing HTTP-only flow and update to include Kafka:

```markdown
### Microservices

```
                    ┌──────────────────┐
                    │  hello-service   │ :8080
                    │  (Orchestrator)  │
                    └────┬─────┬───────┘
                         │     │
              HTTP       │     │  Kafka (async events)
                         │     ▼
      ┌─────────────┐    │  ┌──────────────┐
      │ user-service│◄───┘  │ Kafka Broker │
      │   :8081     │       │  (port 9092) │
      └─────────────┘       └──────┬───────┘
                                   │
      ┌──────────────────┐         │
      │greeting-service  │         ▼
      │   :8082          │  ┌──────────────┐
      └──────────────────┘  │ user-service │
                            │  (Consumer)  │
                            └──────────────┘
```
```

Add a Kafka documentation section after the tech stack table:

```markdown
### Kafka Integration

This demo includes Kafka for asynchronous event streaming alongside synchronous HTTP communication:

- **Producer**: hello-service publishes `GreetingRequestedEvent` events to the `greeting-events` topic
- **Consumer**: user-service consumes events for observability and analytics
- **OTel Tracing**: Kafka producer/consumer spans appear in the same trace as HTTP spans, demonstrating trace context propagation via Kafka headers

**Kafka in Docker Compose:**
```bash
docker compose up -d kafka    # Start Kafka broker
```

**Local development (without Docker):**
Services connect to `localhost:9092` by default. Start Kafka separately or use Docker Compose.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add Kafka integration documentation"
```

---

### Task 11: Local Testing with Docker Compose

**Files:**
- None (manual verification)

- [ ] **Step 1: Start Docker Compose**

Run: `docker compose up -d`
Expected: All services start, including kafka

- [ ] **Step 2: Verify Kafka is running**

Run: `docker compose exec kafka kafka-topics --bootstrap-server localhost:9093 --list`
Expected: Should show topics (possibly empty list initially, topic auto-created on first publish)

- [ ] **Step 3: Build and start services**

Run in separate terminals:
```bash
./gradlew :hello-service:bootRun  # Terminal 1
./gradlew :user-service:bootRun   # Terminal 2
./gradlew :greeting-service:bootRun  # Terminal 3
```

- [ ] **Step 4: Test the API**

Run: `curl http://localhost:8080/api/1`
Expected: Returns greeting, and user-service logs should show "Received greeting event"

- [ ] **Step 5: Check user-service logs**

Look for Kafka consumer log lines in user-service output:
```
Received greeting event: userId=1, language=en, greeting=Hello
```

Expected: Event appears in logs with traceId

---

### Task 12: Final Commit and Verification

- [ ] **Step 1: Review all changes**

Run: `git log --oneline -10`
Expected: See all Kafka-related commits

- [ ] **Step 2: Final build verification**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Push ready**

Run: `git status`
Expected: Working tree clean

---

## Self-Review Checklist

1. **Spec coverage:** ✅ All sections covered:
   - ✅ Kafka broker in compose.yaml (Task 3)
   - ✅ Shared Kafka config (Task 2)
   - ✅ GreetingRequestedEvent DTO (Task 2)
   - ✅ Kafka producer in hello-service (Task 4)
   - ✅ Kafka consumer in user-service (Task 5)
   - ✅ Unit tests (Task 6)
   - ✅ Integration tests (Task 7)
   - ✅ Architecture tests (Task 8)
   - ✅ Documentation (Task 10)
   - ✅ Manual verification (Task 11)

2. **Placeholder scan:** ✅ No TBDs, no vague steps

3. **Type consistency:** ✅ `GreetingRequestedEvent` used consistently across all tasks; constructor signature matches

4. **Completeness:** ✅ Every code change shown in full; every file path specified; commands with expected output

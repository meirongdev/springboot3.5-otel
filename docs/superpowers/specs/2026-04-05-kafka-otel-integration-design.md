# Kafka + OpenTelemetry Integration Design

**Date:** 2026-04-05  
**Status:** Draft  
**Author:** Qwen Code  

---

## 1. Overview

This design adds Kafka producer and consumer to the Spring Boot 3.5 + Java 25 OpenTelemetry demo, demonstrating:

1. **OTel Kafka instrumentation** — Spring Kafka auto-instrumentation creates producer/consumer spans with trace context propagation via Kafka headers
2. **Sync + async pattern coexistence** — HTTP for the main request flow, Kafka for event streaming, both observable in a single OTel trace

The approach is **Kafka Event Bridge** (Approach 1): hello-service publishes user activity events to Kafka while keeping existing HTTP calls for the main flow.

---

## 2. Architecture

### Current State

```
Client → hello-service → user-service (HTTP)
                     → greeting-service (HTTP)
```

All synchronous HTTP, no messaging infrastructure.

### Target State

```
Client → hello-service:8080 → user-service:8081 (HTTP)
                          → greeting-service:8082 (HTTP)
                          → Kafka Topic: greeting-events (async)
                                     ↓
                          user-service:8081 (Kafka Consumer)
```

### OTel Trace Visualization

A single trace will contain both HTTP and Kafka spans:

```
Trace: abc123...
├─ HTTP GET /api/1 (hello-service) ← entry span
│  ├─ HTTP call → user-service
│  ├─ HTTP call → greeting-service
│  └─ Kafka publish → greeting-events (producer span)
│
│  (async gap — Kafka message in broker)
│
├─ Kafka consume ← greeting-events (user-service)
│  └─ consumer span linked by traceId in Kafka headers
```

---

## 3. Components

### 3.1 Shared Module

**New files:**

- `shared/src/main/resources/application-kafka.yaml` — Common Kafka configuration (bootstrap servers, serializers)
- `shared/src/main/java/com/example/shared/kafka/event/GreetingRequestedEvent.java` — Event DTO (Java record)

**application-kafka.yaml:**
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.*"
        spring.json.type.mapping: "greetingRequested:com.example.shared.kafka.event.GreetingRequestedEvent"
```

**GreetingRequestedEvent:**
```java
public record GreetingRequestedEvent(
    String traceId,
    Long userId,
    String language,
    String greeting,
    Instant timestamp
) {}
```

**Dependencies added to `shared/build.gradle.kts`:**
```kotlin
api(platform("org.springframework.kafka:spring-kafka"))
api("org.springframework.kafka:spring-kafka")
```

### 3.2 hello-service (Kafka Producer)

**New files:**

- `hello-service/src/main/java/com/example/hello/service/KafkaEventPublisher.java`
- `hello-service/src/main/java/com/example/hello/config/KafkaTopicConfig.java`

**Modified files:**

- `hello-service/src/main/java/com/example/hello/service/HelloService.java` — Add event publishing after building greeting
- `hello-service/src/main/resources/application.yaml` — Import `application-kafka.yaml`
- `hello-service/build.gradle.kts` — Inherits spring-kafka from shared

**KafkaEventPublisher:**
```java
@Component
public class KafkaEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    @Observed(name = "kafka.event.publish")
    public void publishGreetingRequested(String topic, GreetingRequestedEvent event) {
        try {
            kafkaTemplate.send(topic, event.userId().toString(), event)
                .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to publish greeting event: {}", e.getMessage());
        }
    }
}
```

**KafkaTopicConfig:**
```java
@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic greetingEventsTopic() {
        return new NewTopic("greeting-events", 1, (short) 1);
    }
}
```

**HelloService integration:**
```java
// After building greeting response:
var greeting = greetingClient.getGreeting(language);
kafkaEventPublisher.publishGreetingRequested(
    "greeting-events",
    new GreetingRequestedEvent(traceId, userId, language, greeting, Instant.now())
);
```

### 3.3 user-service (Kafka Consumer)

**New files:**

- `user-service/src/main/java/com/example/user/kafka/GreetingEventConsumer.java`

**Modified files:**

- `user-service/src/main/resources/application.yaml` — Import `application-kafka.yaml`
- `user-service/build.gradle.kts` — Inherits spring-kafka from shared

**GreetingEventConsumer:**
```java
@Component
public class GreetingEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(GreetingEventConsumer.class);

    @KafkaListener(topics = "greeting-events", groupId = "user-service-group")
    @Observed(name = "kafka.event.consume")
    public void handleGreetingRequested(GreetingRequestedEvent event) {
        log.info("Received greeting event: userId={}, language={}, greeting={}",
            event.userId(), event.language(), event.greeting());
    }
}
```

### 3.4 Docker Compose — Kafka Service

**New service in `compose.yaml`:**
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

**Service dependency updates:**
```yaml
hello-service:
  depends_on:
    kafka:
      condition: service_healthy

user-service:
  depends_on:
    kafka:
      condition: service_healthy
```

**Environment variables for services:**
```yaml
environment:
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

---

## 4. Error Handling

### 4.1 Producer Failures

- `@Async` with virtual threads ensures fire-and-forget semantics
- 5-second timeout on `send().get()` prevents blocking
- Failed publishes logged as warnings, do not affect main HTTP flow

### 4.2 Consumer Failures

- Consumer errors logged at ERROR level
- No retry logic for demo; production would use Spring Kafka's `DefaultErrorHandler` with dead-letter topics

### 4.3 Kafka Broker Unavailable

- Services start normally; Kafka operations fail gracefully
- Health check endpoints remain unaffected

---

## 5. Testing

### 5.1 Unit Tests

| Test | Location | Description |
|------|----------|-------------|
| `KafkaEventPublisherTest` | `hello-service/` | Mock `KafkaTemplate`, verify event published with correct data |
| `GreetingEventConsumerTest` | `user-service/` | Verify consumer handles event correctly |

### 5.2 Integration Tests

| Test | Location | Description |
|------|----------|-------------|
| `KafkaIntegrationTest` | `hello-service/` | `@EmbeddedKafka`, verify event published and consumable |
| `HelloControllerEndToEndTest` (updated) | `hello-service/` | Verify Kafka doesn't break existing sync flow |

### 5.3 Test Dependencies

```kotlin
testImplementation("org.springframework.kafka:spring-kafka-test")
testImplementation("org.testcontainers:kafka")
```

### 5.4 EmbeddedKafka Configuration

```java
@EmbeddedKafka(
    partitions = 1,
    topics = {"greeting-events"},
    brokerProperties = {"auto.create.topics.enable=true"}
)
```

---

## 6. Observability Verification

### 6.1 Grafana Dashboard

- Single trace view showing HTTP + Kafka spans
- Metrics: Kafka produce/consume rate, latency (from OTel metrics)

### 6.2 Log Correlation

Consumer logs include `traceId` and `spanId` from OTel context:
```
2026-04-05 TRACE [user-service,abc123,def456] [greeting-events-0-C-1] 
  c.e.u.k.GreetingEventConsumer - Received greeting event: userId=1, language=en
```

### 6.3 Verification Script Update

Update `scripts/verify-otel.sh` to:
1. Verify Kafka spans appear in Tempo traces
2. Verify consumer logs in Loki contain traceId
3. Confirm trace correlation between HTTP and Kafka spans

---

## 7. Architecture Tests

Add to `arch-tests/src/test/java/com/example/`:

```java
@AnalyzeClasses(packages = "com.example.shared.kafka")
class KafkaArchitectureTest {
    @ArchTest
    static void kafka_events_should_be_records = classes()
        .that().resideInAPackage("..kafka.event..")
        .should().beRecords();
}
```

---

## 8. Implementation Order

| Phase | Component | Deliverables |
|-------|-----------|--------------|
| **1** | Infrastructure | Kafka service in compose.yaml, health checks |
| **2** | Shared config | `application-kafka.yaml`, `GreetingRequestedEvent` |
| **3** | Producer | `KafkaEventPublisher`, `KafkaTopicConfig`, HelloService integration |
| **4** | Consumer | `GreetingEventConsumer` in user-service |
| **5** | Tests | Unit tests, `@EmbeddedKafka` integration tests |
| **6** | Verification | Update `verify-otel.sh`, Grafana dashboard |
| **7** | Quality | ArchUnit tests, docs, README updates |

---

## 9. Success Criteria

- [ ] Kafka broker runs in Docker Compose alongside existing services
- [ ] `GET /api/{userId}` triggers Kafka event publish (visible in logs)
- [ ] user-service consumer receives and logs events
- [ ] Grafana trace view shows HTTP + Kafka spans in single trace
- [ ] Consumer logs contain traceId for correlation
- [ ] All tests pass (unit, integration, ArchUnit)
- [ ] Existing HTTP flow unaffected by Kafka integration
- [ ] JaCoCo coverage ≥ 60%

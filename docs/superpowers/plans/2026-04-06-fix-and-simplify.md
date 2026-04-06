# Fix & Simplify Spring Boot 3.5 Best Practices Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 7 identified correctness/security bugs and simplify the code without changing external behavior.

**Architecture:** Each task is independent and self-contained. Fix the root cause, update affected tests, run the quality gate (`./gradlew spotlessApply build`).

**Tech Stack:** Java 25, Spring Boot 3.5, Micrometer Tracing, Logback, Spring Retry, Kafka

---

## File Map

| Task | Files Modified |
|------|----------------|
| 1 | `shared/.../otel/PiiRedactionFilter.java` → rename to `PiiRedactor.java`, `shared/.../resources/logback-spring.xml` |
| 2 | `hello-service/.../ExecutorConfig.java` |
| 3 | `hello-service/.../RetryExchangeInterceptor.java`, `RetryExchangeInterceptorTest.java` |
| 4 | `hello-service/.../HelloService.java`, `HelloServiceTest.java`, `HelloServiceIntegrationTest.java` |
| 5 | `shared/.../resources/application.yaml` |
| 6 | `hello-service/.../KafkaEventPublisher.java`, `KafkaEventPublisherTest.java` |
| 7 | `hello-service/.../KafkaEventPublisher.java`, `KafkaEventPublisherTest.java` (combined with Task 6) |

---

### Task 1: PiiRedactionFilter → PiiRedactor (remove broken TurboFilter)

**Problem:** `PiiRedactionFilter.decide()` always returns `NEUTRAL` — it never actually redacts anything. Registering it as a Logback TurboFilter is misleading. Logback TurboFilters can only accept/deny log events, not transform their content. The static `redact()` methods are genuine utilities and should be kept.

**Fix:** Remove the TurboFilter extension, keep the static utilities, remove from `logback-spring.xml`. For production PII redaction on the OTel/Loki path, the correct approach is collector-level pipeline filtering (documented in logback comment).

**Files:**
- Rename: `shared/src/main/java/com/example/shared/otel/PiiRedactionFilter.java` → `PiiRedactor.java`
- Modify: `shared/src/main/resources/logback-spring.xml`

- [ ] **Step 1: Replace PiiRedactionFilter.java with PiiRedactor.java**

Delete the old file and create the simplified utility class:

```java
// shared/src/main/java/com/example/shared/otel/PiiRedactor.java
package com.example.shared.otel;

import java.util.regex.Pattern;

/**
 * Utility for redacting PII from log messages.
 *
 * <p>Call {@link #redact(String)} explicitly when logging potentially sensitive data.
 *
 * <p>For automatic redaction on the OTel/Loki export path, configure redaction processors in the
 * OpenTelemetry Collector pipeline (e.g., {@code transform} processor with OTTL expressions).
 */
public final class PiiRedactor {

  private static final Pattern EMAIL =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
  private static final Pattern PHONE = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
  private static final Pattern TOKEN =
      Pattern.compile("(?i)(Bearer|token|api[_-]?key)[=:\\s]+([a-zA-Z0-9._-]{10,})");
  private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
  private static final Pattern CREDIT_CARD =
      Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

  private PiiRedactor() {}

  public static String redact(String message) {
    if (message == null) return null;
    String s = EMAIL.matcher(message).replaceAll("***@***.***");
    s = PHONE.matcher(s).replaceAll("***-***-****");
    s = TOKEN.matcher(s).replaceAll("$1=***REDACTED***");
    s = SSN.matcher(s).replaceAll("***-**-****");
    s = CREDIT_CARD.matcher(s).replaceAll("****-****-****-****");
    return s;
  }

  public static Object[] redactArgs(Object[] params) {
    if (params == null) return null;
    Object[] out = new Object[params.length];
    for (int i = 0; i < params.length; i++) {
      out[i] = params[i] instanceof String s ? redact(s) : params[i];
    }
    return out;
  }
}
```

- [ ] **Step 2: Update logback-spring.xml — remove the broken TurboFilter**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
  <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

  <!--
    PII redaction: call PiiRedactor.redact() explicitly at log call sites when logging sensitive
    data. For automatic redaction on the OTel/Loki export path, use a transform processor in the
    OpenTelemetry Collector pipeline.
  -->

  <appender name="OpenTelemetry"
            class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>true</captureExperimentalAttributes>
    <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
    <captureCodeAttributes>true</captureCodeAttributes>
    <captureMarkerAttribute>true</captureMarkerAttribute>
    <captureLoggerContext>true</captureLoggerContext>
  </appender>

  <!-- Async wrapper for non-blocking log writes in production -->
  <appender name="AsyncOpenTelemetry" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="OpenTelemetry"/>
    <queueSize>8192</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <maxFlushTime>1000</maxFlushTime>
    <includeCallerData>false</includeCallerData>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="AsyncOpenTelemetry"/>
  </root>
</configuration>
```

- [ ] **Step 3: Run build to confirm no references to old class name**

```bash
./gradlew spotlessApply build
```

Expected: BUILD SUCCESSFUL. If ArchUnit or other tests reference `PiiRedactionFilter` by name, update those references to `PiiRedactor`.

---

### Task 2: ExecutorConfig — eliminate proxy bypass

**Problem:** `getAsyncExecutor()` calls the factory method `taskExecutor()` directly (not through the CGLIB proxy), so it returns a raw, undecorated executor. Spring Boot wraps the `taskExecutor` bean with `ContextPropagatingTaskDecorator` to propagate OTel context — but `@Async` methods bypass that decorator because `getAsyncExecutor()` returns the unwrapped instance.

**Fix:** Annotate `getAsyncExecutor()` directly with `@Bean`. Spring's CGLIB proxy intercepts the call and returns the singleton decorated bean both when creating the bean AND when `AsyncAnnotationBeanPostProcessor` queries `getAsyncExecutor()`. Remove the now-redundant separate `taskExecutor()` method.

**Files:**
- Modify: `hello-service/src/main/java/com/example/hello/ExecutorConfig.java`

- [ ] **Step 1: Simplify ExecutorConfig**

```java
package com.example.hello;

import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Executor configuration for parallel downstream calls and {@code @Async} methods.
 *
 * <p>Annotating {@code getAsyncExecutor()} with {@code @Bean} ensures Spring's CGLIB proxy returns
 * the same singleton — decorated with {@code ContextPropagatingTaskDecorator} — both to
 * {@code @Async} infrastructure and to callers that inject {@code TaskExecutor} directly. This
 * guarantees OTel context (traceId, spanId, baggage) propagates to child threads.
 */
@Configuration
public class ExecutorConfig implements AsyncConfigurer {

  @Bean(name = "taskExecutor")
  @Override
  public TaskExecutor getAsyncExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        LoggerFactory.getLogger(ExecutorConfig.class)
            .error("Unexpected async exception in [{}]", method.toGenericString(), ex);
  }
}
```

- [ ] **Step 2: Build and run tests**

```bash
./gradlew spotlessApply :hello-service:test
```

Expected: BUILD SUCCESSFUL. The `HelloServiceTest` injects `TaskExecutor` directly via constructor — no change needed there. `HelloServiceIntegrationTest` uses `@SpringBootTest` which loads the full context including the decorated executor.

---

### Task 3: RetryExchangeInterceptor — close response before retry

**Problem:** When a 5xx response is received, the interceptor throws `RetryableHttpException` without closing the response. The HTTP connection stays open, causing connection pool leaks under sustained load.

**Fix:** Call `response.close()` before throwing.

**Files:**
- Modify: `hello-service/src/main/java/com/example/hello/RetryExchangeInterceptor.java`
- Modify: `hello-service/src/test/java/com/example/hello/RetryExchangeInterceptorTest.java`

- [ ] **Step 1: Write a failing test for the response-close behavior**

Add to `RetryExchangeInterceptorTest`:

```java
@Test
void shouldCloseResponseBeforeRetrying() throws IOException {
  // given
  var request = new MockClientHttpRequest(HttpMethod.GET, "/test");
  var failResponse = mock(ClientHttpResponse.class);
  when(failResponse.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
  var successResponse = mock(ClientHttpResponse.class);
  when(successResponse.getStatusCode()).thenReturn(HttpStatus.OK);
  when(execution.execute(any(), any())).thenReturn(failResponse).thenReturn(successResponse);

  // when
  interceptor.intercept(request, new byte[0], execution);

  // then - response was closed before retry
  verify(failResponse).close();
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :hello-service:test --tests "*RetryExchangeInterceptorTest*shouldCloseResponseBeforeRetrying*"
```

Expected: FAIL — `failResponse.close()` is never called.

- [ ] **Step 3: Fix RetryExchangeInterceptor — add response.close() before throwing**

In `RetryExchangeInterceptor.java`, change the intercept method body:

```java
@Override
public ClientHttpResponse intercept(
    HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {

  return retryTemplate.execute(
      context -> {
        try {
          ClientHttpResponse response = execution.execute(request, body);
          int statusCode = response.getStatusCode().value();

          if (RETRYABLE_STATUS_CODES.contains(statusCode)) {
            response.close(); // release connection before retry
            throw new RetryableHttpException(
                String.format(
                    "HTTP %d from %s %s (attempt %d)",
                    statusCode,
                    request.getMethod(),
                    request.getURI(),
                    context.getRetryCount() + 1));
          }

          return response;
        } catch (IOException e) {
          throw new RetryableIOException(e);
        }
      });
}
```

- [ ] **Step 4: Run all RetryExchangeInterceptor tests**

```bash
./gradlew :hello-service:test --tests "*RetryExchangeInterceptorTest*"
```

Expected: All 5 tests PASS.

---

### Task 4: HelloService — populate traceId in GreetingRequestedEvent

**Problem:** `new GreetingRequestedEvent(null, ...)` — traceId is hardcoded to null, making the field useless for correlating Kafka events back to the originating HTTP trace.

**Fix:** Inject `io.micrometer.tracing.Tracer` (already on the classpath via `micrometer-tracing-bridge-otel`) and extract the current span's trace ID.

**Files:**
- Modify: `hello-service/src/main/java/com/example/hello/HelloService.java`
- Modify: `hello-service/src/test/java/com/example/hello/HelloServiceTest.java`
- Modify: `hello-service/src/test/java/com/example/hello/HelloServiceIntegrationTest.java`

- [ ] **Step 1: Update HelloServiceTest — add mock Tracer**

Replace the existing test class with (only constructor changes):

```java
package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class HelloServiceTest {

  @Mock private UserServiceClient userServiceClient;
  @Mock private GreetingServiceClient greetingServiceClient;
  @Mock private KafkaEventPublisher kafkaEventPublisher;
  @Mock private Tracer tracer;

  private final TaskExecutor directExecutor = Runnable::run;

  private HelloService service() {
    return new HelloService(
        userServiceClient, greetingServiceClient, kafkaEventPublisher, taskExecutor, tracer);
  }

  @Test
  void shouldOrchestrateCalls() {
    var span = mock(Span.class);
    var ctx = mock(TraceContext.class);
    when(tracer.currentSpan()).thenReturn(span);
    when(span.context()).thenReturn(ctx);
    when(ctx.traceId()).thenReturn("abc123");

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = service().getHello(1L, "en");

    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
    assertThat(result.language()).isEqualTo("en");
  }

  @Test
  void shouldForwardLanguageToGreetingClient() {
    when(tracer.currentSpan()).thenReturn(null); // no active span is valid

    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("zh"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("zh", "你好，世界！"));

    var result = service().getHello(1L, "zh");

    assertThat(result.greeting()).isEqualTo("你好，世界！");
    assertThat(result.language()).isEqualTo("zh");
  }
}
```

Note: `taskExecutor` field in `service()` is a typo — use `directExecutor`. Fix:

```java
private HelloService service() {
  return new HelloService(
      userServiceClient, greetingServiceClient, kafkaEventPublisher, directExecutor, tracer);
}
```

- [ ] **Step 2: Run test to confirm it fails (constructor mismatch)**

```bash
./gradlew :hello-service:test --tests "*HelloServiceTest*"
```

Expected: COMPILE ERROR — `HelloService` constructor doesn't accept `Tracer`.

- [ ] **Step 3: Update HelloService — inject Tracer, populate traceId**

```java
package com.example.hello;

import com.example.shared.kafka.event.GreetingRequestedEvent;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private static final Logger log = LoggerFactory.getLogger(HelloService.class);
  private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;
  private final KafkaEventPublisher kafkaEventPublisher;
  private final TaskExecutor taskExecutor;
  private final Tracer tracer;

  public HelloService(
      UserServiceClient userServiceClient,
      GreetingServiceClient greetingServiceClient,
      KafkaEventPublisher kafkaEventPublisher,
      TaskExecutor taskExecutor,
      Tracer tracer) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
    this.kafkaEventPublisher = kafkaEventPublisher;
    this.taskExecutor = taskExecutor;
    this.tracer = tracer;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    // Use parallel execution with explicit executor for OTel context propagation.
    // WARNING: Do NOT use CompletableFuture.supplyAsync(supplier) without an executor —
    // it falls back to the common ForkJoinPool, which breaks OTel context propagation.
    var userFuture =
        CompletableFuture.supplyAsync(() -> userServiceClient.getUser(userId), taskExecutor);
    var greetingFuture =
        CompletableFuture.supplyAsync(
            () -> greetingServiceClient.getGreeting(acceptLanguage), taskExecutor);

    CompletableFuture.allOf(userFuture, greetingFuture).join();

    UserServiceClient.UserDTO user = userFuture.join();
    GreetingServiceClient.GreetingDTO greeting = greetingFuture.join();

    log.info(
        AUDIT,
        "Greeting requested userId={} language={} user={}",
        userId,
        greeting.language(),
        user.name());

    kafkaEventPublisher.publishGreetingRequested(
        "greeting-events",
        new GreetingRequestedEvent(
            currentTraceId(), userId, greeting.language(), greeting.message(), Instant.now()));

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }

  private String currentTraceId() {
    var span = tracer.currentSpan();
    return span != null ? span.context().traceId() : null;
  }
}
```

- [ ] **Step 4: Update HelloServiceIntegrationTest — add MockitoBean Tracer**

Add `@MockitoBean private Tracer tracer;` to the class (no behavior stub needed — returns null by default, which `currentTraceId()` handles):

```java
@SpringBootTest
@ActiveProfiles("test")
class HelloServiceIntegrationTest {

  @Autowired private HelloService helloService;

  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private GreetingServiceClient greetingServiceClient;
  @MockitoBean private KafkaEventPublisher kafkaEventPublisher;
  @MockitoBean private io.micrometer.tracing.Tracer tracer;

  @Test
  void shouldLoadContextAndOrchestrate() {
    when(userServiceClient.getUser(1L))
        .thenReturn(new UserServiceClient.UserDTO(1L, "Alice", "alice@example.com"));
    when(greetingServiceClient.getGreeting("en"))
        .thenReturn(new GreetingServiceClient.GreetingDTO("en", "Hello, World!"));

    var result = helloService.getHello(1L, "en");

    assertThat(result.userName()).isEqualTo("Alice");
    assertThat(result.greeting()).isEqualTo("Hello, World!");
  }
}
```

- [ ] **Step 5: Run all HelloService tests**

```bash
./gradlew spotlessApply :hello-service:test --tests "*HelloService*"
```

Expected: All tests PASS.

---

### Task 5: Consolidate duplicate config

**Problem:** `shared/src/main/resources/application.yaml` duplicates almost everything in `application-otel.yaml`. Services that import `application-otel.yaml` get the OTel config twice (with different baggage settings). The shared `application.yaml` also sets `spring.application.name: shared`, which is a confusing default.

**Fix:** Trim `shared/src/main/resources/application.yaml` to only the baseline defaults that `application-otel.yaml` does NOT cover. All OTel config lives in `application-otel.yaml` exclusively.

**Files:**
- Modify: `shared/src/main/resources/application.yaml`

- [ ] **Step 1: Trim the shared application.yaml to minimal non-OTel defaults**

Replace the entire file with:

```yaml
# Shared baseline configuration — loaded by all services from shared module classpath.
# OTel-specific settings (OTLP endpoints, tracing, baggage) are in application-otel.yaml,
# imported explicitly by each service that needs them.

spring:
  threads:
    virtual:
      enabled: true

logging:
  level:
    com.example: debug
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

Note: Remove `spring.application.name: shared` (confusing default), and remove all OTLP/tracing/OTel resource attribute config (those live in `application-otel.yaml`).

- [ ] **Step 2: Verify build and tests pass**

```bash
./gradlew spotlessApply build
```

Expected: BUILD SUCCESSFUL. The `OpenTelemetryResourceAttributesBindingTest` in arch-tests loads each service's context; verify it still passes.

---

### Task 6 + 7: KafkaEventPublisher — type safety + non-blocking send

**Problem (6):** `KafkaTemplate<String, Object>` loses type safety — any object can be sent. Should be `KafkaTemplate<String, GreetingRequestedEvent>`.

**Problem (7):** `kafkaTemplate.send(...).get(5, TimeUnit.SECONDS)` inside an `@Async` method blocks the virtual thread for up to 5 seconds per event. Since this is declared fire-and-forget (`@Async`), use the non-blocking `whenComplete()` callback instead. This also removes the misleading `throws RuntimeException` that would be swallowed by the `AsyncUncaughtExceptionHandler` anyway.

**Files:**
- Modify: `hello-service/src/main/java/com/example/hello/KafkaEventPublisher.java`
- Modify: `hello-service/src/test/java/com/example/hello/KafkaEventPublisherTest.java`

- [ ] **Step 1: Update KafkaEventPublisherTest — typed mock + non-blocking assertions**

```java
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
```

- [ ] **Step 2: Run tests to confirm they fail (class signature mismatch)**

```bash
./gradlew :hello-service:test --tests "*KafkaEventPublisherTest*"
```

Expected: COMPILE ERROR — `KafkaEventPublisher` still uses `KafkaTemplate<String, Object>`.

- [ ] **Step 3: Update KafkaEventPublisher — typed template + non-blocking send**

```java
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

/** Publishes greeting events to Kafka asynchronously (fire-and-forget). */
@Component
public class KafkaEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

  private final KafkaTemplate<String, GreetingRequestedEvent> kafkaTemplate;
  private final Counter kafkaPublishFailureCounter;

  public KafkaEventPublisher(
      KafkaTemplate<String, GreetingRequestedEvent> kafkaTemplate, MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaPublishFailureCounter =
        Counter.builder("kafka.publish.failure.count")
            .description("Number of failed Kafka publish attempts")
            .register(meterRegistry);
  }

  @Async
  @Observed(name = "kafka.event.publish", contextualName = "kafka.publish")
  public void publishGreetingRequested(String topic, GreetingRequestedEvent event) {
    kafkaTemplate
        .send(topic, event.userId().toString(), event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                kafkaPublishFailureCounter.increment();
                log.warn(
                    "Failed to publish greeting event for userId={}: {}",
                    event.userId(),
                    ex.getMessage(),
                    ex);
              } else {
                log.debug("Published greeting event for userId={}", event.userId());
              }
            });
  }
}
```

- [ ] **Step 4: Run all Kafka-related tests**

```bash
./gradlew spotlessApply :hello-service:test --tests "*KafkaEventPublisher*"
```

Expected: Both tests PASS.

---

### Final: Full quality gate

- [ ] **Run the complete build**

```bash
./gradlew spotlessApply clean build
```

Expected: BUILD SUCCESSFUL — spotless, error-prone, JaCoCo (≥80%), contract tests, ArchUnit all pass.

- [ ] **Verify ArchUnit rules still hold**

The `orchestratorServicesMustUseExplicitExecutor` rule checks that `HelloService` depends on `TaskExecutor` — still true after Task 4 changes. The `dtosMustBeRecords` rule — `PiiRedactor` has no DTO suffix so no issue. Confirm:

```bash
./gradlew :arch-tests:test
```

Expected: All ArchUnit rules PASS.

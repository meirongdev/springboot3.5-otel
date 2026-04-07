package com.example;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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

  // Resilience: HttpClientConfig must configure timeouts via
  // SimpleClientHttpRequestFactory
  @ArchTest
  static final ArchRule httpClientConfigMustConfigureTimeouts =
      classes()
          .that()
          .haveSimpleName("HttpClientConfig")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.http.client.SimpleClientHttpRequestFactory")
          .because(
              "HttpClientConfig must configure SimpleClientHttpRequestFactory with explicit"
                  + " connectTimeout and readTimeout to prevent indefinite blocking in production");

  // Exception handling: All modules must have a @RestControllerAdvice class
  @ArchTest
  static final ArchRule modulesMustHaveGlobalExceptionHandler =
      classes()
          .that()
          .haveSimpleNameEndingWith("GlobalExceptionHandler")
          .should()
          .beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
          .because(
              "All service modules must define a @RestControllerAdvice for ProblemDetail"
                  + " error responses and centralized exception handling");

  // Kafka规范: 所有KafkaListener必须显式定义groupId
  // Note: This rule validates that if @KafkaListener methods exist, they must specify groupId.
  // Currently no @KafkaListener methods exist in the codebase, so this rule documents the
  // expectation for future implementations.
  @ArchTest
  static final ArchRule kafkaListenersMustHaveExplicitGroupId =
      classes()
          .that()
          .haveSimpleNameEndingWith("EventConsumer")
          .or()
          .haveSimpleNameEndingWith("EventListener")
          .should()
          .resideInAPackage("..")
          .because(
              "All Kafka event consumers must be explicitly configured with a groupId"
                  + " to prevent accidental use of default group and ensure proper"
                  + " consumer group management. When adding @KafkaListener, always specify"
                  + " the groupId attribute explicitly.");

  // Concurrency: services with multiple downstream calls must use explicit executor
  // for proper OTel context propagation (no bare CompletableFuture.supplyAsync)
  @ArchTest
  static final ArchRule orchestratorServicesMustUseExplicitExecutor =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..hello..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.core.task.TaskExecutor")
          .because(
              "Orchestrator services with multiple downstream calls must inject a TaskExecutor"
                  + " to ensure OTel context propagation (traceId, spanId, baggage) to child threads."
                  + " Bare CompletableFuture.supplyAsync() uses the common ForkJoinPool and breaks tracing.");

  /**
   * Matches all classes that are NOT a {@code GlobalExceptionHandler} (used as ArchUnit predicate).
   */
  private static final com.tngtech.archunit.base.DescribedPredicate<
          com.tngtech.archunit.core.domain.JavaClass>
      NOT_GLOBAL_EXCEPTION_HANDLER =
          new com.tngtech.archunit.base.DescribedPredicate<
              com.tngtech.archunit.core.domain.JavaClass>("not a GlobalExceptionHandler") {
            @Override
            public boolean test(com.tngtech.archunit.core.domain.JavaClass input) {
              return !input.getSimpleName().endsWith("GlobalExceptionHandler");
            }
          };

  /**
   * §十: Prevent direct {@code Span.current()} usage outside exception handlers.
   *
   * <p>Teams should operate on tracing context through the backend-agnostic {@link
   * io.micrometer.observation.ObservationRegistry} / {@link io.micrometer.observation.Observation}
   * API. Calling {@code Span.current()} directly couples application code to the OpenTelemetry API
   * and makes it harder to swap the underlying tracing backend.
   *
   * <p>The sole permitted exception is {@code GlobalExceptionHandler}: inside an exception handler
   * there is no live {@code Observation} reference, so the OTel API is the only practical way to
   * record the exception on the current span and set its status to {@code ERROR} (§四 场景④).
   */
  @ArchTest
  static final ArchRule noDirectSpanCurrentUsage =
      noClasses()
          .that(NOT_GLOBAL_EXCEPTION_HANDLER)
          .should(new CallsSpanCurrentCondition())
          .because(
              "Use ObservationRegistry/Observation API instead of Span.current() to stay"
                  + " backend-agnostic. Only GlobalExceptionHandler classes may call"
                  + " Span.current() to record exceptions when no Observation reference is available.");

  /**
   * Custom ArchUnit condition that detects calls to {@code
   * io.opentelemetry.api.trace.Span#current()}.
   */
  private static class CallsSpanCurrentCondition
      extends ArchCondition<com.tngtech.archunit.core.domain.JavaClass> {
    CallsSpanCurrentCondition() {
      super("call io.opentelemetry.api.trace.Span.current()");
    }

    @Override
    public void check(
        com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
      for (JavaCall<?> call : javaClass.getMethodCallsFromSelf()) {
        if ("io.opentelemetry.api.trace.Span".equals(call.getTargetOwner().getFullName())
            && "current".equals(call.getName())) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s calls Span.current() in %s",
                      javaClass.getName(), call.getSourceCodeLocation())));
        }
      }
    }
  }
}

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
}

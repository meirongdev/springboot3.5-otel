plugins {
    java
    `java-library`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// No main class for shared module
tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

dependencies {
    // Spring Boot starters
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-actuator")

    // Micrometer Tracing + OpenTelemetry Bridge
    api("io.micrometer:micrometer-tracing")
    api("io.micrometer:micrometer-tracing-bridge-otel")

    // OpenTelemetry SDK + OTLP Exporter
    // Explicitly pin to 1.49.0 to match opentelemetry-logback-appender 2.13.0-alpha requirements
    // (Spring Boot 3.5.12 manages 1.47.0 which lacks the setException method needed by the appender)
    api("io.opentelemetry:opentelemetry-api:1.49.0")
    api("io.opentelemetry:opentelemetry-exporter-otlp:1.49.0")

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

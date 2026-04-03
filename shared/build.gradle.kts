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
    api("io.opentelemetry:opentelemetry-api")
    api("io.opentelemetry:opentelemetry-exporter-otlp")

    // Micrometer OTLP Registry for metrics
    api("io.micrometer:micrometer-registry-otlp")

    // OpenTelemetry Logback Appender (from instrumentation project)
    api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.26.1-alpha")

    // Spring Boot autoconfigure for OTel
    implementation("org.springframework.boot:spring-boot-starter-aop")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
}

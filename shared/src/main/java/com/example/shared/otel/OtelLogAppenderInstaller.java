package com.example.shared.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Connects the OpenTelemetry SDK to the Logback appender declared in logback-spring.xml.
 *
 * <p><b>Why this is needed:</b> Spring Boot 3.5 auto-configures the {@code OpenTelemetry} bean and
 * supports OTLP log export via {@code management.otlp.logging.endpoint}. However, the Logback →
 * OTLP bridge still requires the {@code OpenTelemetryAppender} from the OpenTelemetry
 * instrumentation project (alpha). This component programmatically installs the Spring-managed
 * {@code OpenTelemetry} instance into the Logback appender, which Spring Boot does not do
 * automatically.
 *
 * <p><b>Architecture:</b>
 *
 * <pre>
 *   Application Logs → Logback → OpenTelemetryAppender → OTel SDK → OTLP → Collector
 * </pre>
 *
 * <p><b>Future consideration:</b> Monitor Spring Boot releases for native Logback appender
 * auto-installation, which would eliminate the need for this manual bridge.
 */
public class OtelLogAppenderInstaller {

  private static final Logger log = LoggerFactory.getLogger(OtelLogAppenderInstaller.class);

  @Autowired(required = false)
  private OpenTelemetry openTelemetry;

  @PostConstruct
  void install() {
    if (openTelemetry != null) {
      OpenTelemetryAppender.install(openTelemetry);
      log.debug("OpenTelemetry Logback appender installed successfully");
    } else {
      log.warn(
          "OpenTelemetry bean not found - OTLP log export will not work. "
              + "Ensure spring-boot-starter-actuator and micrometer-tracing are on the classpath.");
    }
  }
}

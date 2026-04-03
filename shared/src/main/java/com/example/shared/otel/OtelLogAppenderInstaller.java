package com.example.shared.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Connects the OpenTelemetry SDK to the Logback appender declared in logback-spring.xml. Spring
 * Boot 3.5 auto-configures the OpenTelemetry bean; this component bridges it to the Logback
 * appender for OTLP log export.
 */
@Component
public class OtelLogAppenderInstaller {

  @Autowired(required = false)
  private OpenTelemetry openTelemetry;

  @PostConstruct
  void install() {
    if (openTelemetry != null) {
      OpenTelemetryAppender.install(openTelemetry);
    }
  }
}

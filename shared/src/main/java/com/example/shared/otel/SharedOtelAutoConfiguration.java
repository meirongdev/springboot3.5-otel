package com.example.shared.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for OpenTelemetry log bridge in the shared module.
 *
 * <p>This configuration registers the {@link OtelLogAppenderInstaller} bean that connects the
 * Spring-managed {@link OpenTelemetry} instance to the Logback {@code OpenTelemetryAppender}.
 *
 * <p><b>Why this is needed:</b> Spring Boot 3.5 auto-configures the {@code OpenTelemetry} bean and
 * supports OTLP log export via {@code management.otlp.logging.endpoint}. However, the Logback →
 * OTLP bridge still requires the {@code OpenTelemetryAppender} from the OpenTelemetry
 * instrumentation project (alpha). This auto-configuration programmatically installs the
 * Spring-managed {@code OpenTelemetry} instance into the Logback appender, which Spring Boot does
 * not do automatically.
 *
 * <p><b>Architecture:</b>
 *
 * <pre>
 *   Application Logs → Logback → OpenTelemetryAppender → OTel SDK → OTLP → Collector
 * </pre>
 *
 * <p><b>Activation:</b> This auto-configuration activates only when both the OTel SDK is on the
 * classpath and {@code management.otlp.logging.endpoint} is configured. The {@link
 * OtelLogAppenderInstaller} itself uses {@code @Autowired(required = false)} to gracefully handle
 * cases where the {@code OpenTelemetry} bean is not yet available.
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(
    prefix = "management.otlp.logging",
    name = "endpoint",
    matchIfMissing = false)
public class SharedOtelAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OtelLogAppenderInstaller otelLogAppenderInstaller() {
    return new OtelLogAppenderInstaller();
  }
}

package com.example.shared.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Configures OpenTelemetry SDK for log export. */
@Configuration
public class OpenTelemetryLogsConfig {

  @Value("${spring.application.name:unknown}")
  private String applicationName;

  @Value("${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}")
  private String tracesEndpoint;

  @Bean
  public SdkLoggerProvider sdkLoggerProvider() {
    // Derive logs endpoint from traces endpoint
    String logsEndpoint = tracesEndpoint.replace("/v1/traces", "/v1/logs");

    OtlpHttpLogRecordExporter logExporter =
        OtlpHttpLogRecordExporter.builder().setEndpoint(logsEndpoint).build();

    Resource resource =
        Resource.builder().put(ServiceAttributes.SERVICE_NAME, applicationName).build();

    return SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(logExporter)
                .setScheduleDelay(Duration.ofSeconds(1))
                .build())
        .build();
  }

  @Bean
  @Primary
  public OpenTelemetry openTelemetry(SdkLoggerProvider sdkLoggerProvider) {
    return OpenTelemetrySdk.builder().setLoggerProvider(sdkLoggerProvider).build();
  }
}

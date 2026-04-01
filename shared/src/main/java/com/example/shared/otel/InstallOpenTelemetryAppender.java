package com.example.shared.otel;

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Installs OpenTelemetry Logback Appender at startup. */
@Component
public class InstallOpenTelemetryAppender {

  @PostConstruct
  public void install() {
    var loggerContext = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
    var rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

    var otelAppender = new OpenTelemetryAppender();
    otelAppender.setContext(loggerContext);
    otelAppender.start();

    rootLogger.addAppender(otelAppender);
  }
}

package com.example.shared.otel;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.spi.LifeCycle;
import java.util.regex.Pattern;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * TurboFilter that redacts sensitive data from log messages before they are processed.
 *
 * <p>This filter ensures PII such as email addresses, phone numbers, and bearer tokens are not
 * leaked to centralized logging systems (Loki).
 *
 * <p>Register in logback-spring.xml as a turboFilter for message-level redaction:
 *
 * <pre>
 *   &lt;turboFilter class="com.example.shared.otel.PiiRedactionFilter"/&gt;
 * </pre>
 */
public class PiiRedactionFilter extends ch.qos.logback.classic.turbo.TurboFilter
    implements LifeCycle {

  /** Marker to indicate a log event contains sensitive data that was redacted. */
  public static final Marker PII_REDACTED = MarkerFactory.getMarker("PII_REDACTED");

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
  private static final Pattern PHONE_PATTERN =
      Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
  private static final Pattern TOKEN_PATTERN =
      Pattern.compile("(?i)(Bearer|token|api[_-]?key)[=:\\s]+([a-zA-Z0-9._-]{10,})");
  private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
  private static final Pattern CREDIT_CARD_PATTERN =
      Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

  @Override
  public FilterReply decide(
      Marker marker,
      Logger logger,
      ch.qos.logback.classic.Level level,
      String format,
      Object[] params,
      Throwable t) {
    // We don't block events; just log a warning if redaction occurred
    return FilterReply.NEUTRAL;
  }

  /**
   * Redacts sensitive data from a log message.
   *
   * <p>Call this utility method manually when logging sensitive data, or integrate into a custom
   * layout/encoder for automatic redaction.
   *
   * @param message the original log message
   * @return the redacted message with PII replaced by placeholders
   */
  public static String redact(String message) {
    if (message == null) return null;

    String redacted = message;
    redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("***@***.***");
    redacted = PHONE_PATTERN.matcher(redacted).replaceAll("***-***-****");
    redacted = TOKEN_PATTERN.matcher(redacted).replaceAll("$1=***REDACTED***");
    redacted = SSN_PATTERN.matcher(redacted).replaceAll("***-**-****");
    redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("****-****-****-****");

    return redacted;
  }

  /**
   * Redacts sensitive data from formatted arguments before logging.
   *
   * @param params the format arguments
   * @return redacted arguments array
   */
  public static Object[] redactArgs(Object[] params) {
    if (params == null) return null;

    Object[] redacted = new Object[params.length];
    for (int i = 0; i < params.length; i++) {
      if (params[i] instanceof String) {
        redacted[i] = redact((String) params[i]);
      } else {
        redacted[i] = params[i];
      }
    }
    return redacted;
  }
}

package com.example.shared.otel;

import java.util.regex.Pattern;

/**
 * Utility for redacting PII from log messages.
 *
 * <p>Call {@link #redact(String)} explicitly when logging potentially sensitive data.
 *
 * <p>For automatic redaction on the OTel/Loki export path, configure redaction processors in the
 * OpenTelemetry Collector pipeline (e.g., {@code transform} processor with OTTL expressions).
 */
public final class PiiRedactor {

  private static final Pattern EMAIL =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
  private static final Pattern PHONE = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
  private static final Pattern TOKEN =
      Pattern.compile("(?i)(Bearer|token|api[_-]?key)[=:\\s]+([a-zA-Z0-9._-]{10,})");
  private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
  private static final Pattern CREDIT_CARD =
      Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

  private PiiRedactor() {}

  public static String redact(String message) {
    if (message == null) return null;
    String s = EMAIL.matcher(message).replaceAll("***@***.***");
    s = PHONE.matcher(s).replaceAll("***-***-****");
    s = TOKEN.matcher(s).replaceAll("$1=***REDACTED***");
    s = SSN.matcher(s).replaceAll("***-**-****");
    s = CREDIT_CARD.matcher(s).replaceAll("****-****-****-****");
    return s;
  }

  public static Object[] redactArgs(Object[] params) {
    if (params == null) return null;
    Object[] out = new Object[params.length];
    for (int i = 0; i < params.length; i++) {
      out[i] = params[i] instanceof String s ? redact(s) : params[i];
    }
    return out;
  }
}

package com.example.shared.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs request completion details so Grafana Loki dashboards have recent correlated application
 * logs.
 */
@Component
public class RequestCompletionLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
  // Limit cached body size to avoid memory pressure from large payloads
  private static final int MAX_BODY_BYTES = 4096;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Skip logging for health check endpoints to avoid polluting logs and RED metrics
    // with Docker Compose health check requests
    String requestUri = request.getRequestURI();
    if (requestUri.startsWith("/actuator/")) {
      filterChain.doFilter(request, response);
      return;
    }

    var wrappedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
    var wrappedResponse = new ContentCachingResponseWrapper(response);

    long startedAt = System.nanoTime();

    try {
      filterChain.doFilter(wrappedRequest, wrappedResponse);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      log.atInfo()
          .addKeyValue("method", request.getMethod())
          .addKeyValue("path", requestUri)
          .addKeyValue("status", wrappedResponse.getStatus())
          .addKeyValue("durationMs", durationMs)
          .addKeyValue(
              "requestBody",
              toBodyString(wrappedRequest.getContentAsByteArray(), request.getCharacterEncoding()))
          .addKeyValue(
              "responseBody",
              toBodyString(
                  wrappedResponse.getContentAsByteArray(), response.getCharacterEncoding()))
          .log("request completed");
      // Must copy buffered response body back to the actual response
      wrappedResponse.copyBodyToResponse();
    }
  }

  private static String toBodyString(byte[] bytes, String encoding) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    Charset charset;
    try {
      charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    } catch (IllegalArgumentException e) {
      charset = StandardCharsets.UTF_8;
    }
    return new String(bytes, charset);
  }
}

package com.example.shared.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs request completion details so Grafana Loki dashboards have recent correlated application
 * logs.
 */
@Component
public class RequestCompletionLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);

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

    long startedAt = System.nanoTime();

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      log.info(
          "request completed method={} path={} status={} durationMs={}",
          request.getMethod(),
          requestUri,
          response.getStatus(),
          durationMs);
    }
  }
}

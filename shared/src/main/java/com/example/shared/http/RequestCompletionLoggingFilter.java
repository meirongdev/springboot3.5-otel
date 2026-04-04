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
    long startedAt = System.nanoTime();

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      log.info(
          "request completed method={} path={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
    }
  }
}

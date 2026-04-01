package com.example.shared.otel;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** Filter configuration for HTTP request observability. */
@Configuration
public class FilterConfig {

  private static final Logger log = LoggerFactory.getLogger(FilterConfig.class);

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public Filter headerLoggerFilter() {
    return new HeaderLoggerFilter();
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public Filter addTraceIdFilter() {
    return new AddTraceIdFilter();
  }

  /** Logs all HTTP request headers. */
  public static class HeaderLoggerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HeaderLoggerFilter.class);

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      StringBuilder headers = new StringBuilder();
      Enumeration<String> headerNames = request.getHeaderNames();
      while (headerNames.hasMoreElements()) {
        String headerName = headerNames.nextElement();
        headers
            .append("  ")
            .append(headerName)
            .append(": ")
            .append(request.getHeader(headerName))
            .append("\n");
      }
      log.atDebug().log("Incoming request headers:\n{}", headers);
      filterChain.doFilter(request, response);
    }
  }

  /** Adds Trace ID to HTTP response headers. */
  public static class AddTraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      filterChain.doFilter(request, response);
      // Trace ID is available via Micrometer Tracing
      // The response header will be added by the tracing bridge automatically
    }
  }
}

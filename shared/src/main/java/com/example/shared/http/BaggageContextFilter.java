package com.example.shared.http;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads business context from incoming HTTP headers and writes it into OTel Baggage.
 *
 * <p>Fields listed in {@code management.tracing.baggage.remote-fields} are automatically forwarded
 * to downstream services via the W3C {@code baggage} header on every outgoing {@code RestClient} /
 * {@code WebClient} request, so downstream services receive the values without any extra code.
 *
 * <p>Fields listed in {@code management.tracing.baggage.correlation.fields} are automatically
 * written to MDC by Spring Boot, so every log line carries them without manual {@code MDC.put()}.
 *
 * <p>This filter must run after the tracing filter (which establishes the OTel context) but before
 * business logic. {@code Ordered.HIGHEST_PRECEDENCE + 10} satisfies this constraint.
 *
 * <p>Required configuration in {@code application-otel.yaml}:
 *
 * <pre>{@code
 * management:
 *   tracing:
 *     baggage:
 *       remote-fields: [tenant-id, request-source, user-type]
 *       correlation:
 *         fields: [tenant-id, request-source, user-type]
 * }</pre>
 *
 * <p><b>API Note:</b> Uses {@link Tracer#createBaggageInScope(String, String)} (Micrometer Tracing
 * 1.5 / Spring Boot 3.5 API) instead of {@code BaggageField} (Spring Boot 4.0+). Baggage scopes are
 * created here and closed in {@code finally} so they remain active for the entire request including
 * downstream calls. See <a
 * href="https://docs.spring.io/spring-boot/reference/actuator/tracing.html">Spring Boot Tracing
 * Docs</a>.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BaggageContextFilter extends OncePerRequestFilter {

  // Custom headers this service accepts from API gateways or upstream callers
  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
  private static final String USER_TYPE_HEADER = "X-User-Type";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  // Baggage field names — must match management.tracing.baggage.remote-fields
  private static final String TENANT_ID = "tenant-id";
  private static final String REQUEST_SOURCE = "request-source";
  private static final String USER_TYPE = "user-type";

  private final Tracer tracer;

  public BaggageContextFilter(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String tenantId = request.getHeader(TENANT_ID_HEADER);
    String requestSource = request.getHeader(REQUEST_SOURCE_HEADER);
    String userType = request.getHeader(USER_TYPE_HEADER);

    // Generate a stable request ID if not provided by the caller
    String requestId =
        request.getHeader(REQUEST_ID_HEADER) != null
                && !request.getHeader(REQUEST_ID_HEADER).isBlank()
            ? request.getHeader(REQUEST_ID_HEADER)
            : UUID.randomUUID().toString();

    // Open baggage scopes for the entire request — they must stay active through
    // chain.doFilter() so downstream RestClient/WebClient calls can propagate them.
    List<BaggageInScope> scopes = new ArrayList<>();
    try {
      if (tenantId != null && !tenantId.isBlank()) {
        scopes.add(tracer.createBaggageInScope(TENANT_ID, tenantId));
      }
      if (requestSource != null && !requestSource.isBlank()) {
        scopes.add(tracer.createBaggageInScope(REQUEST_SOURCE, requestSource));
      }
      if (userType != null && !userType.isBlank()) {
        scopes.add(tracer.createBaggageInScope(USER_TYPE, userType));
      }

      // Echo the request ID back so callers can correlate their logs with ours
      response.setHeader(REQUEST_ID_HEADER, requestId);

      chain.doFilter(request, response);
    } finally {
      // Close all scopes in reverse order to clean up properly
      for (int i = scopes.size() - 1; i >= 0; i--) {
        scopes.get(i).close();
      }
    }
  }
}

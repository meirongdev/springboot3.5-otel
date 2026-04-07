package com.example.hello;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/** Global exception handler for hello-service. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceAccessException.class)
  public ProblemDetail handleDownstreamUnavailable(ResourceAccessException ex) {
    // §四 场景④: mark the current span as ERROR and record the full exception stack trace.
    // recordException() writes exception details to span events; setStatus(ERROR) ensures
    // the span shows as failed in APM error-rate dashboards.
    Span.current().recordException(ex);
    Span.current().setStatus(StatusCode.ERROR, ex.getMessage());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY, "Downstream service unavailable: " + ex.getMessage());
    problem.setTitle("Downstream Service Unavailable");
    problem.setType(URI.create("about:blank"));
    return problem;
  }

  @ExceptionHandler(RuntimeException.class)
  public ProblemDetail handleRuntimeException(RuntimeException ex) {
    Span.current().recordException(ex);
    Span.current().setStatus(StatusCode.ERROR, ex.getMessage());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    problem.setTitle("Internal Server Error");
    problem.setType(URI.create("about:blank"));
    return problem;
  }
}

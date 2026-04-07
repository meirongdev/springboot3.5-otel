package com.example.user;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Global exception handler for user-service. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
    // §四 场景④: mark span as ERROR and attach exception stack trace to span events.
    Span.current().recordException(ex);
    Span.current().setStatus(StatusCode.ERROR, ex.getMessage());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "User not found with id: " + ex.getUserId());
    problem.setTitle("User Not Found");
    problem.setType(URI.create("about:blank"));
    problem.setProperty("userId", ex.getUserId());
    return problem;
  }
}

package com.example.hello;

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
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY, "Downstream service unavailable: " + ex.getMessage());
    problem.setTitle("Downstream Service Unavailable");
    problem.setType(URI.create("about:blank"));
    return problem;
  }

  @ExceptionHandler(RuntimeException.class)
  public ProblemDetail handleRuntimeException(RuntimeException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    problem.setTitle("Internal Server Error");
    problem.setType(URI.create("about:blank"));
    return problem;
  }
}

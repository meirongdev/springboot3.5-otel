package com.example.greeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void shouldReturnProblemDetailForRuntimeException() {
    // given
    var exception = new RuntimeException("Something went wrong");

    // when
    ProblemDetail problem = handler.handleRuntimeException(exception);

    // then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
    assertThat(problem.getDetail()).isEqualTo("Something went wrong");
    assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
  }

  @Test
  void shouldHandleExceptionWithNullMessage() {
    // given
    var exception = new RuntimeException((String) null);

    // when
    ProblemDetail problem = handler.handleRuntimeException(exception);

    // then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
  }
}

package com.example.shared.http;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestCompletionLoggingFilterTest {

  @Test
  void shouldLogRequestMethodPathAndStatus(CapturedOutput output) throws Exception {
    var filter = new RequestCompletionLoggingFilter();
    var request = new MockHttpServletRequest("GET", "/api/1");
    var response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> markSuccessful(servletResponse);

    filter.doFilter(request, response, chain);

    assertThat(output)
        .contains("method=GET")
        .contains("path=/api/1")
        .contains("status=200");
  }

  private static void markSuccessful(jakarta.servlet.ServletResponse servletResponse)
      throws IOException {
    ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
  }
}

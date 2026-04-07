package com.example.shared.http;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    FilterChain chain = (req, resp) -> markSuccessful(resp);

    filter.doFilter(request, response, chain);

    // addKeyValue() produces quoted values: method="GET" path="/api/1" status="200"
    assertThat(output)
        .contains("method=\"GET\"")
        .contains("path=\"/api/1\"")
        .contains("status=\"200\"");
  }

  @Test
  void shouldLogRequestBody(CapturedOutput output) throws Exception {
    var filter = new RequestCompletionLoggingFilter();
    var request = new MockHttpServletRequest("POST", "/api/greet");
    request.setContent("{\"name\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    request.setContentType("application/json");
    var response = new MockHttpServletResponse();
    // Chain reads the body so ContentCachingRequestWrapper can buffer it
    FilterChain chain =
        (req, resp) -> {
          ((HttpServletRequest) req).getInputStream().readAllBytes();
          markSuccessful(resp);
        };

    filter.doFilter(request, response, chain);

    // addKeyValue() wraps value in quotes; JSON content inside is not escaped
    assertThat(output).contains("requestBody=\"{\"name\":\"world\"}\"");
  }

  @Test
  void shouldLogResponseBody(CapturedOutput output) throws Exception {
    var filter = new RequestCompletionLoggingFilter();
    var request = new MockHttpServletRequest("GET", "/api/1");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (req, resp) -> {
          var httpResp = (HttpServletResponse) resp;
          httpResp.setStatus(HttpServletResponse.SC_OK);
          httpResp.getWriter().write("{\"greeting\":\"hello\"}");
        };

    filter.doFilter(request, response, chain);

    // addKeyValue() wraps value in quotes; JSON content inside is not escaped
    assertThat(output).contains("responseBody=\"{\"greeting\":\"hello\"}\"");
  }

  @Test
  void shouldSkipActuatorPaths(CapturedOutput output) throws Exception {
    var filter = new RequestCompletionLoggingFilter();
    var request = new MockHttpServletRequest("GET", "/actuator/health");
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, resp) -> markSuccessful(resp);

    filter.doFilter(request, response, chain);

    assertThat(output).doesNotContain("request completed");
  }

  private static void markSuccessful(jakarta.servlet.ServletResponse servletResponse)
      throws IOException {
    ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
  }
}

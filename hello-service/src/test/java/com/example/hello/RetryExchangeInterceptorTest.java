package com.example.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

@ExtendWith(MockitoExtension.class)
class RetryExchangeInterceptorTest {

  private RetryExchangeInterceptor interceptor;

  @Mock private ClientHttpRequestExecution execution;

  @BeforeEach
  void setUp() {
    interceptor = new RetryExchangeInterceptor();
  }

  @Test
  void shouldNotRetryOnSuccessfulResponse() throws IOException {
    // given
    var request = new MockClientHttpRequest(HttpMethod.GET, "/test");
    var response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);
    when(execution.execute(any(), any())).thenReturn(response);

    // when
    var result = interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldRetryOn5xxErrors() throws IOException {
    // given
    var request = new MockClientHttpRequest(HttpMethod.GET, "/test");
    var successResponse = mock(ClientHttpResponse.class);
    when(successResponse.getStatusCode()).thenReturn(HttpStatus.OK);

    var failResponse = mock(ClientHttpResponse.class);
    when(failResponse.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);

    // First 2 calls return 503, then success
    when(execution.execute(any(), any()))
        .thenReturn(failResponse)
        .thenReturn(failResponse)
        .thenReturn(successResponse);

    // when
    var result = interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldNotRetryOn4xxErrors() throws IOException {
    // given
    var request = new MockClientHttpRequest(HttpMethod.GET, "/test");
    var response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    when(execution.execute(any(), any())).thenReturn(response);

    // when
    var result = interceptor.intercept(request, new byte[0], execution);

    // then - should return 404 immediately without retry
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldThrowAfterMaxRetriesExhausted() throws IOException {
    // given
    var request = new MockClientHttpRequest(HttpMethod.GET, "/test");
    var failResponse = mock(ClientHttpResponse.class);
    when(failResponse.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
    when(execution.execute(any(), any())).thenReturn(failResponse);

    // when/then - the interceptor throws RetryableHttpException after max retries
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("HTTP 500")
        .hasMessageContaining("attempt 3");
  }
}

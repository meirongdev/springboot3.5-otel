package com.example.hello;

import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * RestClient interceptor that adds retry with exponential backoff for failed HTTP requests.
 *
 * <p>Retries on: 5xx server errors, IOExceptions (connection failures)
 *
 * <p>Does NOT retry on: 4xx client errors
 */
public class RetryExchangeInterceptor implements ClientHttpRequestInterceptor {

  private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(500, 502, 503, 504);
  private static final int MAX_RETRIES = 3;

  private final RetryTemplate retryTemplate;

  public RetryExchangeInterceptor() {
    this.retryTemplate = buildRetryTemplate();
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {

    return retryTemplate.execute(
        context -> {
          try {
            ClientHttpResponse response = execution.execute(request, body);
            int statusCode = response.getStatusCode().value();

            if (RETRYABLE_STATUS_CODES.contains(statusCode)) {
              response.close(); // release connection before retry
              throw new RetryableHttpException(
                  String.format(
                      "HTTP %d from %s %s (attempt %d)",
                      statusCode,
                      request.getMethod(),
                      request.getURI(),
                      context.getRetryCount() + 1));
            }

            return response;
          } catch (IOException e) {
            throw new RetryableIOException(e);
          }
        });
  }

  private RetryTemplate buildRetryTemplate() {
    RetryTemplate template = new RetryTemplate();

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(MAX_RETRIES);

    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(100);
    backOff.setMaxInterval(1000);
    backOff.setMultiplier(2.0);

    template.setRetryPolicy(retryPolicy);
    template.setBackOffPolicy(backOff);

    return template;
  }

  /** Custom exception to trigger retry from interceptor. */
  private static class RetryableHttpException extends RuntimeException {
    RetryableHttpException(String message) {
      super(message);
    }
  }

  /** Wrapper for IOException to enable retry. */
  private static class RetryableIOException extends RuntimeException {
    RetryableIOException(IOException cause) {
      super(cause);
    }
  }
}

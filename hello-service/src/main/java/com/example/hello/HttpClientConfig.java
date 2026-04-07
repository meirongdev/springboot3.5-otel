package com.example.hello;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client configuration for service-to-service calls. */
@Configuration
public class HttpClientConfig {

  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));

    return builder
        .requestFactory(requestFactory)
        .requestInterceptor(new RetryExchangeInterceptor())
        .build();
  }

  @Bean
  public UserServiceClient userServiceClient(
      RestClient restClient,
      @Value("${user.service.url:http://localhost:18081}") String userServiceUrl) {
    return new UserServiceClient(restClient, userServiceUrl);
  }

  @Bean
  public GreetingServiceClient greetingServiceClient(
      RestClient restClient,
      @Value("${greeting.service.url:http://localhost:18082}") String greetingServiceUrl) {
    return new GreetingServiceClient(restClient, greetingServiceUrl);
  }
}

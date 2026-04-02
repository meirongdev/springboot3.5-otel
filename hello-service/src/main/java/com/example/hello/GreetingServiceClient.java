package com.example.hello;

import com.example.shared.http.AcceptLanguageNormalizer;
import java.net.URI;
import org.springframework.web.client.RestClient;

/** Client for greeting-service. */
public class GreetingServiceClient {

  private final RestClient restClient;
  private final String greetingServiceUrl;

  public GreetingServiceClient(RestClient restClient, String greetingServiceUrl) {
    this.restClient = restClient;
    this.greetingServiceUrl = greetingServiceUrl;
  }

  public GreetingDTO getGreeting(String acceptLanguage) {
    String normalizedLanguage = AcceptLanguageNormalizer.normalize(acceptLanguage);
    return restClient
        .get()
        .uri(URI.create(greetingServiceUrl + "/api/greetings"))
        .header("Accept-Language", normalizedLanguage)
        .retrieve()
        .body(GreetingDTO.class);
  }

  public record GreetingDTO(String language, String message) {}
}

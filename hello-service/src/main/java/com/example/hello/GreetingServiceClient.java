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
    GreetingDTO greeting =
        restClient
            .get()
            .uri(URI.create(greetingServiceUrl + "/api/greetings"))
            .header("Accept-Language", normalizedLanguage)
            .retrieve()
            .body(GreetingDTO.class);
    if (greeting == null) {
      throw new IllegalStateException(
          "Greeting service returned empty body for language=" + normalizedLanguage);
    }
    return greeting;
  }

  public record GreetingDTO(String language, String message) {}
}

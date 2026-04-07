package com.example.greeting;

import com.example.shared.http.AcceptLanguageNormalizer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Greeting controller - returns localized greetings via {@link GreetingService}. */
@RestController
public class GreetingController {

  private final GreetingService greetingService;

  public GreetingController(GreetingService greetingService) {
    this.greetingService = greetingService;
  }

  @GetMapping("/api/greetings")
  public Greeting getGreeting(
      @RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguage) {
    String language = AcceptLanguageNormalizer.normalize(acceptLanguage);
    return greetingService.getGreeting(language);
  }
}

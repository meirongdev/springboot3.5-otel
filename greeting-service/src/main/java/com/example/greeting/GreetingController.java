package com.example.greeting;

import com.example.shared.http.AcceptLanguageNormalizer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Greeting controller - returns localized greetings. */
@RestController
public class GreetingController {

  @GetMapping("/api/greetings")
  public Greeting getGreeting(
      @RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguage) {
    String language = AcceptLanguageNormalizer.normalize(acceptLanguage);

    String message =
        switch (language) {
          case "zh" -> "你好，世界！";
          case "ja" -> "こんにちは世界！";
          default -> "Hello, World!";
        };

    return new Greeting(language, message);
  }
}

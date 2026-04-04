package com.example.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Hello controller - orchestrates user and greeting services. */
@RestController
public class HelloController {

  private final HelloService helloService;

  public HelloController(HelloService helloService) {
    this.helloService = helloService;
  }

  @GetMapping("/api/{userId}")
  public HelloResponse getHello(
      @PathVariable Long userId,
      @RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguage) {
    return helloService.getHello(userId, acceptLanguage);
  }
}

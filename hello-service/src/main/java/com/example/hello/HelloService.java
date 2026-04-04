package com.example.hello;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

/** Hello service - orchestrates calls to user-service and greeting-service. */
@Service
public class HelloService {

  private final UserServiceClient userServiceClient;
  private final GreetingServiceClient greetingServiceClient;

  public HelloService(
      UserServiceClient userServiceClient, GreetingServiceClient greetingServiceClient) {
    this.userServiceClient = userServiceClient;
    this.greetingServiceClient = greetingServiceClient;
  }

  @Observed(name = "hello.service.getHello", contextualName = "getHello")
  public HelloResponse getHello(Long userId, String acceptLanguage) {
    UserServiceClient.UserDTO user = userServiceClient.getUser(userId);
    GreetingServiceClient.GreetingDTO greeting = greetingServiceClient.getGreeting(acceptLanguage);

    return new HelloResponse(user.id(), user.name(), greeting.message(), greeting.language());
  }
}

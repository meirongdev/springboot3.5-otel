package com.example.hello;

import io.micrometer.observation.annotation.Observed;
import org.springframework.scheduling.annotation.Async;
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
  public HelloController.HelloResponse getHello(Long userId, String acceptLanguage) {
    UserServiceClient.UserDTO user = userServiceClient.getUser(userId);
    GreetingServiceClient.GreetingDTO greeting = greetingServiceClient.getGreeting(acceptLanguage);

    return new HelloController.HelloResponse(
        user.id(), user.name(), greeting.message(), greeting.language());
  }

  @Async
  @Observed(name = "hello.service.getHelloAsync", contextualName = "getHelloAsync")
  public HelloController.HelloResponse getHelloAsync(Long userId, String acceptLanguage) {
    return getHello(userId, acceptLanguage);
  }
}

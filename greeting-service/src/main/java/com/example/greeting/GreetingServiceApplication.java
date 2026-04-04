package com.example.greeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.greeting", "com.example.shared"})
public class GreetingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GreetingServiceApplication.class, args);
  }
}

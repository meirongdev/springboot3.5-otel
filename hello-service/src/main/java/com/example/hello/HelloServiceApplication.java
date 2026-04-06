package com.example.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.example.hello", "com.example.shared"})
@EnableAsync
@EnableRetry
public class HelloServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(HelloServiceApplication.class, args);
  }
}

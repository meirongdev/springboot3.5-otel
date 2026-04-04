package com.example.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.hello", "com.example.shared"})
public class HelloServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(HelloServiceApplication.class, args);
  }
}

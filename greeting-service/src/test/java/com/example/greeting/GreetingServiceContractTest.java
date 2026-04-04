package com.example.greeting;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class GreetingServiceContractTest {

  @Autowired private WebApplicationContext context;

  @BeforeEach
  void setup() {
    RestAssuredMockMvc.webAppContextSetup(context);
  }
}

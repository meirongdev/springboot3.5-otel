package com.example.hello;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "greeting-service")
class GreetingServicePactConsumerTest {

  @Pact(consumer = "hello-service", provider = "greeting-service")
  V4Pact englishGreeting(PactDslWithProvider builder) {
    return builder
        .given("greeting service is up")
        .uponReceiving("get English greeting")
        .path("/api/greetings")
        .method("GET")
        .matchHeader("Accept-Language", ".*en.*", "en")
        .willRespondWith()
        .status(200)
        .body(
            newJsonBody(
                    body -> {
                      body.stringType("language", "en");
                      body.stringType("message", "Hello, World!");
                    })
                .build())
        .toPact(V4Pact.class);
  }

  @Test
  @PactTestFor(pactMethod = "englishGreeting")
  void shouldParseGreetingResponse(MockServer mockServer) {
    var client = new GreetingServiceClient(RestClient.builder().build(), mockServer.getUrl());

    var greeting = client.getGreeting("en");

    assertThat(greeting.language()).isEqualTo("en");
    assertThat(greeting.message()).isEqualTo("Hello, World!");
  }
}

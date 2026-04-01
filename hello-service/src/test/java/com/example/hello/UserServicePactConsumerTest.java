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
@PactTestFor(providerName = "user-service")
class UserServicePactConsumerTest {

  @Pact(consumer = "hello-service", provider = "user-service")
  V4Pact userExists(PactDslWithProvider builder) {
    return builder
        .given("user 1 exists")
        .uponReceiving("get user by id")
        .path("/api/users/1")
        .method("GET")
        .willRespondWith()
        .status(200)
        .body(
            newJsonBody(
                    body -> {
                      body.integerType("id", 1);
                      body.stringType("name", "Alice");
                      body.stringType("email", "alice@example.com");
                    })
                .build())
        .toPact(V4Pact.class);
  }

  @Test
  @PactTestFor(pactMethod = "userExists")
  void shouldParseUserResponse(MockServer mockServer) {
    var client = new UserServiceClient(RestClient.builder().build(), mockServer.getUrl());

    var user = client.getUser(1L);

    assertThat(user.id()).isEqualTo(1L);
    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.email()).isEqualTo("alice@example.com");
  }
}

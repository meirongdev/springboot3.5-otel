package com.example.hello;

import java.net.URI;
import org.springframework.web.client.RestClient;

/** Client for user-service. */
public class UserServiceClient {

  private final RestClient restClient;
  private final String userServiceUrl;

  public UserServiceClient(RestClient restClient, String userServiceUrl) {
    this.restClient = restClient;
    this.userServiceUrl = userServiceUrl;
  }

  public UserDTO getUser(Long userId) {
    UserDTO user =
        restClient
            .get()
            .uri(URI.create(userServiceUrl + "/api/users/" + userId))
            .retrieve()
            .body(UserDTO.class);
    if (user == null) {
      throw new IllegalStateException("User service returned empty body for userId=" + userId);
    }
    return user;
  }

  public record UserDTO(Long id, String name, String email) {}
}

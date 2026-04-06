package com.example.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest {

  @Autowired private UserService userService;

  @Test
  void shouldFindSeededUser() {
    var user = userService.findById(1L);

    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.email()).isEqualTo("alice@example.com");
  }

  @Test
  void shouldThrowForUnknownUser() {
    assertThatThrownBy(() -> userService.findById(999L))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("User not found");
  }
}

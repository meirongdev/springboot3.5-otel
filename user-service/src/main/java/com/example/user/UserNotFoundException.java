package com.example.user;

/** Domain exception for user not found scenarios. */
public class UserNotFoundException extends RuntimeException {

  private final Long userId;

  public UserNotFoundException(Long userId) {
    super("User not found: " + userId);
    this.userId = userId;
  }

  public Long getUserId() {
    return userId;
  }
}

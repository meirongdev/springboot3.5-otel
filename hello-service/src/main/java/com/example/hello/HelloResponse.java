package com.example.hello;

/** Response combining user data and localized greeting. */
public record HelloResponse(Long userId, String userName, String greeting, String language) {}

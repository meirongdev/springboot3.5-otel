package com.example.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** User entity. */
@Table("user")
public record User(@Id Long id, String name, String email) {}

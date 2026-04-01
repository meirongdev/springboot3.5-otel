package com.example.user;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** User repository. */
@Repository
public interface UserRepository extends ListCrudRepository<User, Long> {}

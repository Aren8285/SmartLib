package com.smartlib.repository;

import com.smartlib.model.Role;
import com.smartlib.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    List<User> findByRoleAndActiveTrue(Role role);

    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
}


package com.smartlib.dto;

import com.smartlib.model.Role;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private Role role;
    private int points;
    private List<String> preferredSubjects;
    private Instant createdAt;
}

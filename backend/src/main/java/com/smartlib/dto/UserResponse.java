package com.smartlib.dto;

import com.smartlib.model.Role;
import lombok.Data;

@Data
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private Role role;
    private int points;
}


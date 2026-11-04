package com.smartlib.dto;

import lombok.Data;

import java.util.List;

@Data
public class AuthResponse {
    private String token;
    private UserResponse user;
}

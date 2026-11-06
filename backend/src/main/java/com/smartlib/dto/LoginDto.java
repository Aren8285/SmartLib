package com.smartlib.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDto {

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 200, message = "Email must be at most 200 characters.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(max = 100, message = "Password must be at most 100 characters.")
    private String password;
}

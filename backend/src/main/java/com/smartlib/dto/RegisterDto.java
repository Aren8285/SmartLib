package com.smartlib.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Students only; role is always assigned STUDENT server-side. */
@Data
public class RegisterDto {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters.")
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 200, message = "Email must be at most 200 characters.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
            message = "Password must contain at least one letter and one digit.")
    private String password;

    @Size(max = 10, message = "Choose at most 10 preferred subjects.")
    private List<@Size(max = 60, message = "Subject names are too long.") String> preferredSubjects;
}

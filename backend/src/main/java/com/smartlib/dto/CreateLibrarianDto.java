package com.smartlib.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Librarian-created librarian accounts; role is always LIBRARIAN server-side. */
@Data
public class CreateLibrarianDto {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 60, message = "Name must be 2-60 characters.")
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
            message = "Password must contain at least one letter and one digit.")
    private String password;
}

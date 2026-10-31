package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LibrarianActionDto {
    @NotBlank(message = "librarianId is required")
    private String librarianId;
}


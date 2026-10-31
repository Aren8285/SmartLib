package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoanRequestDto {
    @NotBlank(message = "studentId is required")
    private String studentId;

    @NotBlank(message = "bookId is required")
    private String bookId;
}


package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request comes from the authenticated principal; only the book is needed. */
@Data
public class LoanRequestDto {

    @NotBlank(message = "bookId is required.")
    private String bookId;
}

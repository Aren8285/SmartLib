package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request comes from the authenticated principal; only the book is needed. */
@Data
public class LoanRequestDto {

    @NotBlank(message = "bookId is required.")
    @Size(max = 64, message = "bookId is too long.")
    private String bookId;
}

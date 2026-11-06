package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitReflectionDto {

    @NotBlank(message = "loanId is required.")
    @Size(max = 64, message = "loanId is too long.")
    private String loanId;

    @NotBlank(message = "Content is required.")
    @Size(max = 2000, message = "A reflection must be at most 2000 characters.")
    private String content;
}

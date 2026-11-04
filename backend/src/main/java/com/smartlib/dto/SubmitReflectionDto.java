package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitReflectionDto {

    @NotBlank(message = "loanId is required.")
    private String loanId;

    @NotBlank(message = "Content is required.")
    private String content;
}

package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Rejection note — required (validated in the controller for this endpoint only). */
@Data
public class ReviewReflectionDto {
    private String reviewNote;
}

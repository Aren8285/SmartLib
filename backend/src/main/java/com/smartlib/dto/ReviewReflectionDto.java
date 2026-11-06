package com.smartlib.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Rejection note — required (validated in the service for this endpoint only). */
@Data
public class ReviewReflectionDto {

    @Size(max = 500, message = "The review note must be at most 500 characters.")
    private String reviewNote;
}

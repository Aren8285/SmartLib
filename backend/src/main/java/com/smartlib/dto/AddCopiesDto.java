package com.smartlib.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Librarian adds physical copies; defaults to GOOD condition. */
@Data
public class AddCopiesDto {

    @Min(value = 1, message = "At least one copy must be added.")
    @Max(value = 20, message = "Add at most 20 copies at a time.")
    private int count = 1;

    @Size(max = 20, message = "Condition must be at most 20 characters.")
    private String condition = "GOOD";
}

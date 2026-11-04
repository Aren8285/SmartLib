package com.smartlib.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Librarian adds physical copies; defaults to GOOD condition. */
@Data
public class AddCopiesDto {

    @Min(value = 1, message = "At least one copy must be added.")
    @Max(value = 10, message = "Add at most 10 copies at a time.")
    private int count = 1;

    private String condition = "GOOD";
}

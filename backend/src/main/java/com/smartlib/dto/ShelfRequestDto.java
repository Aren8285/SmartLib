package com.smartlib.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShelfRequestDto {

    @NotBlank(message = "Shelf code is required.")
    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$",
            message = "Shelf code must be 1-10 letters, digits or dashes.")
    private String code;

    @NotBlank(message = "Section is required.")
    @Size(max = 80, message = "Section must be at most 80 characters.")
    private String section;

    @Min(value = 1, message = "Row must be at least 1.")
    @Max(value = 20, message = "Row must be at most 20.")
    private int row;

    @Min(value = 1, message = "Column must be at least 1.")
    @Max(value = 20, message = "Column must be at most 20.")
    private int column;

    @Min(value = 1, message = "Capacity must be at least 1.")
    @Max(value = 500, message = "Capacity must be at most 500.")
    private int capacity;

    @Size(max = 100, message = "Label must be at most 100 characters.")
    private String label;
}

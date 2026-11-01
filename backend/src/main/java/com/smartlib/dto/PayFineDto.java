package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayFineDto {
    @NotBlank(message = "studentId is required")
    private String studentId;
}

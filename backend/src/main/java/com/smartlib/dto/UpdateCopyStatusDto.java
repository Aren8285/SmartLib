package com.smartlib.dto;

import com.smartlib.model.CopyStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCopyStatusDto {

    @NotNull(message = "A new status is required.")
    private CopyStatus status;

    @Size(max = 20, message = "Condition must be at most 20 characters.")
    private String condition;
}

package com.smartlib.dto;

import com.smartlib.model.CopyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCopyStatusDto {

    @NotNull(message = "A new status is required.")
    private CopyStatus status;

    private String condition;
}

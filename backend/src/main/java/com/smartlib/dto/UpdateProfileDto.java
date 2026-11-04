package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Self-service profile update (name and preferred subjects only; email/role are fixed). */
@Data
public class UpdateProfileDto {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 60, message = "Name must be 2-60 characters.")
    private String name;

    private List<String> preferredSubjects;
}

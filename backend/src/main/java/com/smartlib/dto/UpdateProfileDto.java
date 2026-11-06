package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Self-service profile update (name and preferred subjects only; email/role are fixed). */
@Data
public class UpdateProfileDto {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters.")
    private String name;

    @Size(max = 10, message = "Choose at most 10 preferred subjects.")
    private List<@Size(max = 60, message = "Subject names are too long.") String> preferredSubjects;
}

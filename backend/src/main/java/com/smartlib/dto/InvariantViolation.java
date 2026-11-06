package com.smartlib.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One invariant violation reported by the consistency checker. */
@Data
@AllArgsConstructor
public class InvariantViolation {
    private String type;
    private String details;
}

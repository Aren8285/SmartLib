package com.smartlib.dto;

import com.smartlib.model.WaitlistStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class WaitlistEntryDto {
    private int position;
    private String entryId;
    private String studentId;
    private String studentName;
    private String loanId;
    private Instant joinedAt;
    private WaitlistStatus status;
}

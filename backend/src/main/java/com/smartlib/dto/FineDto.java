package com.smartlib.dto;

import com.smartlib.model.FineReason;
import com.smartlib.model.FineStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class FineDto {
    private String id;
    private String studentId;
    private String studentName;
    private String loanId;
    private double amount;
    private FineReason reason;
    private Instant issueDate;
    private Instant paidDate;
    private FineStatus status;
}

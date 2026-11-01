package com.smartlib.dto;

import com.smartlib.model.LoanStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class LoanResponse {
    private String id;
    private String studentId;
    private String studentName;
    private String bookId;
    private String bookTitle;
    private String copyId;
    private LoanStatus status;
    private Instant requestDate;
    private Instant issueDate;
    private Instant dueDate;
    private Instant returnDate;
    private Instant pickupDeadline;
    private Integer queuePosition;
    private Double accruedFine;
    private FineDto fine;
    private String message;
}


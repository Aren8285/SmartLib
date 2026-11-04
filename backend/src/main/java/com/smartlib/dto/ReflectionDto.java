package com.smartlib.dto;

import com.smartlib.model.ReflectionStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class ReflectionDto {
    private String id;
    private String studentId;
    private String studentName;
    private String loanId;
    private String bookId;
    private String bookTitle;
    private String content;
    private Instant submissionDate;
    private ReflectionStatus status;
    private String reviewedBy;
    private Instant reviewedDate;
    private String reviewNote;
}

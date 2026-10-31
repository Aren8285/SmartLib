package com.smartlib.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "loans")
public class Loan {

    @Id
    private String id;

    private String studentId;
    private String bookId;
    private String copyId;

    private LoanStatus status;

    private Instant requestDate;
    private Instant issueDate;
    private Instant dueDate;
    private Instant returnDate;
}


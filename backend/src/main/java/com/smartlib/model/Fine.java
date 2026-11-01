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
@Document(collection = "fines")
public class Fine {

    @Id
    private String id;

    private String studentId;
    private String loanId;

    private double amount;

    private FineReason reason;

    private Instant issueDate;
    private Instant paidDate;

    @Builder.Default
    private FineStatus status = FineStatus.PENDING;
}

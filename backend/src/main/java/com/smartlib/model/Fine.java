package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private FineStatus status;

    /** Breakdown for combined fines (damaged return + late days). Null for single-cause fines. */
    private Double lateAmount;
    private Double damagedAmount;
}

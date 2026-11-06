package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "loans")
@CompoundIndex(name = "loan_student_status_idx", def = "{'studentId': 1, 'status': 1}")
@CompoundIndex(name = "loan_status_pickup_idx", def = "{'status': 1, 'pickupDeadline': 1}")
@CompoundIndex(name = "loan_status_due_idx", def = "{'status': 1, 'dueDate': 1}")
@CompoundIndex(name = "loan_status_issue_idx", def = "{'status': 1, 'issueDate': 1}")
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

    /** Set when a copy is reserved (READY_FOR_PICKUP), not only when issued. */
    private Instant pickupDeadline;

    /** One-time pickup reminder flag (used when the deadline is < 6h away). */
    private boolean reminderSent;

    /** One-time due-date reminder flag (used when the due date is < 24h away). */
    private boolean dueReminderSent;

    /** Nullable; set when a fine (late return / lost / damaged book) is attached to the loan. */
    private String fineId;
}

package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "reflections")
public class Reflection {

    @Id
    private String id;

    @Indexed(unique = false) // one reflection per loan is enforced in the service
    private String studentId;
    private String loanId;
    private String bookId;
    private String content;
    private Instant submissionDate;
    private ReflectionStatus status;
    /** Librarian who reviewed, when reviewed. */
    private String reviewedBy;
    private Instant reviewedDate;
    /** Required note on rejection. */
    private String reviewNote;
}

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

    @Indexed
    private String studentId;

    /** One reflection per loan; the unique sparse index is the concurrency backstop. */
    @Indexed(unique = true, sparse = true)
    private String loanId;

    private String bookId;
    private String content;
    private Instant submissionDate;

    @Indexed
    private ReflectionStatus status;

    /** Librarian who reviewed, when reviewed. */
    private String reviewedBy;
    private Instant reviewedDate;
    /** Required note on rejection. */
    private String reviewNote;
}

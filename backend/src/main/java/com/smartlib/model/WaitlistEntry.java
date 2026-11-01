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
@Document(collection = "waitlist")
public class WaitlistEntry {

    @Id
    private String id;

    private String bookId;
    private String studentId;
    private String loanId;

    private Instant joinedAt;

    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;
}

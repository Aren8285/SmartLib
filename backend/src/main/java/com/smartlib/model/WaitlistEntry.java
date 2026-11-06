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
@Document(collection = "waitlist")
@CompoundIndex(name = "waitlist_book_status_joined_idx",
        def = "{'bookId': 1, 'status': 1, 'joinedAt': 1}")
public class WaitlistEntry {

    @Id
    private String id;

    private String bookId;
    private String studentId;
    private String loanId;

    private Instant joinedAt;

    /** Set when the queue entry is fulfilled; used by the wait-time report. */
    private Instant fulfilledAt;

    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;
}

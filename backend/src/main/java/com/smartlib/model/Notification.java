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
@Document(collection = "notifications")
@CompoundIndex(name = "notification_user_read_created_idx",
        def = "{'userId': 1, 'read': 1, 'createdAt': -1}")
public class Notification {

    @Id
    private String id;

    private String userId;
    private NotificationType type;
    private String title;
    private String message;
    private boolean read;
    private Instant createdAt;
    /** Nullable; links the notification to the loan it concerns. */
    private String relatedLoanId;
}

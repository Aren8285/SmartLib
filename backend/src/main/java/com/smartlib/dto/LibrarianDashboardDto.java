package com.smartlib.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LibrarianDashboardDto {
    private long readyForPickup;
    private long issued;
    private long overdue;
    private long pendingReflections;
    private long pendingFines;
    private long totalBooks;
    private long totalCopies;
    private Map<String, Long> copiesByStatus;
    private List<ActionItem> oldestActionsNeeded;
}

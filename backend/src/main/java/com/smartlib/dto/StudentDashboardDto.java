package com.smartlib.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class StudentDashboardDto {
    private int points;
    private long activeLoans;          // READY_FOR_PICKUP + ISSUED + OVERDUE
    private long waitlisted;
    private double pendingFinesAmount; // sum of PENDING fine amounts
    private long pendingFinesCount;
    private long reflectionsPending;   // loans awaiting a reflection
    private Instant nextDueDate;
    private List<ActionItem> actionsNeeded;
    private Map<String, Long> counts;
    private long unreadNotifications;
}

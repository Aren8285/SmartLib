package com.smartlib.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response shapes for the librarian reports. Kept together so they stay in sync. */
public final class ReportDtos {

    private ReportDtos() {
    }

    @Data
    public static class OverdueRow {
        private String loanId;
        private String studentId;
        private String studentName;
        private String bookId;
        private String bookTitle;
        private Instant dueDate;
        private long daysLate;
        private double accruedFine;
    }

    @Data
    public static class PopularBook {
        private int rank;
        private String bookId;
        private String title;
        private String author;
        private String subject;
        private long borrowCount;
    }

    @Data
    public static class LeaderboardRow {
        private int rank;
        private String studentId;
        private String name;
        private int points;
    }

    @Data
    public static class CirculationPoint {
        private String date;
        private long issued;
        private long returned;
    }

    @Data
    public static class CirculationReport {
        private Instant from;
        private Instant to;
        private long totalIssued;
        private long totalReturned;
        private List<CirculationPoint> points;
    }

    @Data
    public static class FineReasonRow {
        private String reason;
        private long count;
        private double imposed;
        private double collected;
        private double outstanding;
    }

    @Data
    public static class FinesReport {
        private Instant from;
        private Instant to;
        private double totalImposed;
        private double totalCollected;
        private double totalOutstanding;
        private List<FineReasonRow> byReason;
    }

    @Data
    public static class WaitlistRow {
        private String bookId;
        private String title;
        private long queueLength;
        /** Average hours from joining the queue to the copy becoming ready; null when never fulfilled. */
        private Double averageWaitHours;
    }

    @Data
    public static class WaitlistReport {
        private Instant from;
        private Instant to;
        private Double overallAverageWaitHours;
        private List<WaitlistRow> books;
    }

    @Data
    public static class BookBrief {
        private String bookId;
        private String title;
        private String author;
        private String subject;
        private String isbn;
        private long totalCopies;
        private long availableCopies;
    }

    @Data
    public static class InventoryReport {
        private long totalCopies;
        private Map<String, Long> copiesByStatus;
        private List<BookBrief> booksWithZeroAvailable;
    }
}

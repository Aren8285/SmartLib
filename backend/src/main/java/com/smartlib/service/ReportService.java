package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.ReportDtos.*;
import com.smartlib.model.Book;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Librarian reports. Period reports use MongoDB aggregation pipelines
 * ($match/$group/$lookup); point reads use the indexes declared on the models.
 * Default period is the last 30 days.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final WaitlistRepository waitlistRepository;
    private final MongoTemplate mongoTemplate;
    private final AppProperties app;

    public record DateRange(LocalDate start, LocalDate end, Instant from, Instant toExclusive) {
    }

    public DateRange range(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' must be on or before 'to'.");
        }
        Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(start, end, fromInstant, toExclusive);
    }

    // ─────────── overdue ───────────

    public List<OverdueRow> overdue() {
        List<Loan> loans = loanRepository.findByStatus(LoanStatus.OVERDUE,
                Sort.by(Sort.Direction.ASC, "dueDate"));
        Set<String> userIds = loans.stream().map(Loan::getStudentId).collect(Collectors.toSet());
        Set<String> bookIds = loans.stream().map(Loan::getBookId).collect(Collectors.toSet());
        Map<String, String> studentNames = userRepositoryNames(userIds);
        Map<String, String> bookTitles = bookTitles(bookIds);

        Instant now = Instant.now();
        List<OverdueRow> rows = new ArrayList<>();
        for (Loan loan : loans) {
            OverdueRow row = new OverdueRow();
            row.setLoanId(loan.getId());
            row.setStudentId(loan.getStudentId());
            row.setStudentName(studentNames.getOrDefault(loan.getStudentId(), "Unknown"));
            row.setBookId(loan.getBookId());
            row.setBookTitle(bookTitles.getOrDefault(loan.getBookId(), "Unknown"));
            row.setDueDate(loan.getDueDate());
            long daysLate = loan.getDueDate() == null ? 0
                    : Math.max(1, (long) Math.ceil(
                    Math.max(0, ChronoUnit.SECONDS.between(loan.getDueDate(), now)) / 86400.0));
            row.setDaysLate(daysLate);
            row.setAccruedFine(daysLate * (double) app.getFinePerDay());
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong(OverdueRow::getDaysLate).reversed());
        return rows;
    }

    private Map<String, String> userRepositoryNames(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return userRepositoryMap(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
    }

    private Map<String, User> userRepositoryMap(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), User.class).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<String, String> bookTitles(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return bookRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Book::getId, Book::getTitle));
    }

    // ─────────── popular books ───────────

    public List<PopularBook> popularBooks(LocalDate from, LocalDate to, int limit) {
        DateRange range = range(from, to);
        int capped = Math.max(1, Math.min(50, limit));
        List<Document> docs = aggregate("loans", List.of(
                match(new Document("issueDate",
                        new Document("$gte", java.util.Date.from(range.from()))
                                .append("$lt", java.util.Date.from(range.toExclusive())))),
                // Loan.bookId is stored as a String, but the book _id is an ObjectId.
                // Convert before grouping so the $lookup below can match.
                new Document("$group", new Document("_id",
                        new Document("$toObjectId", "$bookId"))
                        .append("borrowCount", new Document("$sum", 1))),
                new Document("$sort", new Document("borrowCount", -1)),
                new Document("$limit", capped),
                new Document("$lookup", new Document("from", mongoTemplate.getCollectionName(Book.class))
                        .append("localField", "_id")
                        .append("foreignField", "_id")
                        .append("as", "book")),
                new Document("$unwind", "$book")
        ));
        List<PopularBook> result = new ArrayList<>();
        int rank = 1;
        for (Document doc : docs) {
            Document book = doc.get("book", Document.class);
            if (book == null) continue;
            PopularBook row = new PopularBook();
            row.setRank(rank++);
            row.setBookId(String.valueOf(doc.get("_id")));
            row.setTitle(book.getString("title"));
            row.setAuthor(book.getString("author"));
            row.setSubject(book.getString("subject"));
            row.setBorrowCount(((Number) doc.get("borrowCount")).longValue());
            result.add(row);
        }
        return result;
    }

    // ─────────── leaderboard ───────────

    /** Names and points only; no email or any other personal data. */
    public List<LeaderboardRow> leaderboard(int limit) {
        int capped = Math.max(1, Math.min(50, limit));
        Query query = Query.query(Criteria.where("role").is(Role.STUDENT))
                .with(Sort.by(Sort.Direction.DESC, "points")
                        .and(Sort.by(Sort.Direction.ASC, "name")))
                .limit(capped);
        List<User> users = mongoTemplate.find(query, User.class);
        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (User user : users) {
            LeaderboardRow row = new LeaderboardRow();
            row.setRank(rank++);
            row.setStudentId(user.getId());
            row.setName(user.getName());
            row.setPoints(user.getPoints());
            rows.add(row);
        }
        return rows;
    }

    // ─────────── circulation ───────────

    public CirculationReport circulation(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        Map<String, Long> issued = dailyCounts("issueDate", range);
        Map<String, Long> returned = dailyCounts("returnDate", range);

        List<CirculationPoint> points = new ArrayList<>();
        long totalIssued = 0;
        long totalReturned = 0;
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            String key = DAY.format(day);
            long i = issued.getOrDefault(key, 0L);
            long r = returned.getOrDefault(key, 0L);
            CirculationPoint point = new CirculationPoint();
            point.setDate(key);
            point.setIssued(i);
            point.setReturned(r);
            points.add(point);
            totalIssued += i;
            totalReturned += r;
        }
        CirculationReport report = new CirculationReport();
        report.setFrom(range.from());
        report.setTo(range.toExclusive());
        report.setTotalIssued(totalIssued);
        report.setTotalReturned(totalReturned);
        report.setPoints(points);
        return report;
    }

    private Map<String, Long> dailyCounts(String field, DateRange range) {
        List<Document> docs = aggregate("loans", List.of(
                match(new Document(field, new Document("$gte", java.util.Date.from(range.from()))
                        .append("$lt", java.util.Date.from(range.toExclusive())))),
                new Document("$group", new Document("_id", new Document("$dateToString",
                        new Document("format", "%Y-%m-%d")
                                .append("date", "$" + field)
                                .append("timezone", "UTC")))
                        .append("count", new Document("$sum", 1)))
        ));
        Map<String, Long> result = new HashMap<>();
        for (Document doc : docs) {
            result.put(String.valueOf(doc.get("_id")), ((Number) doc.get("count")).longValue());
        }
        return result;
    }

    // ─────────── fines ───────────

    public FinesReport fines(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<Document> docs = aggregate("fines", List.of(
                match(new Document("issueDate", new Document("$gte", java.util.Date.from(range.from()))
                        .append("$lt", java.util.Date.from(range.toExclusive())))),
                new Document("$group", new Document("_id",
                        new Document("reason", "$reason").append("status", "$status"))
                        .append("count", new Document("$sum", 1))
                        .append("total", new Document("$sum", "$amount")))
        ));

        Map<String, FineReasonRow> byReason = new LinkedHashMap<>();
        double totalImposed = 0;
        double totalCollected = 0;
        double totalOutstanding = 0;
        for (Document doc : docs) {
            Document key = doc.get("_id", Document.class);
            if (key == null) continue;
            String reason = String.valueOf(key.get("reason"));
            String status = String.valueOf(key.get("status"));
            long count = ((Number) doc.get("count")).longValue();
            double total = ((Number) doc.get("total")).doubleValue();

            FineReasonRow row = byReason.computeIfAbsent(reason, r -> {
                FineReasonRow fresh = new FineReasonRow();
                fresh.setReason(r);
                return fresh;
            });
            row.setCount(row.getCount() + count);
            row.setImposed(row.getImposed() + total);
            if ("PAID".equals(status)) {
                row.setCollected(row.getCollected() + total);
                totalCollected += total;
            } else {
                row.setOutstanding(row.getOutstanding() + total);
                totalOutstanding += total;
            }
            totalImposed += total;
        }

        FinesReport report = new FinesReport();
        report.setFrom(range.from());
        report.setTo(range.toExclusive());
        report.setTotalImposed(round2(totalImposed));
        report.setTotalCollected(round2(totalCollected));
        report.setTotalOutstanding(round2(totalOutstanding));
        report.setByReason(byReason.values().stream()
                .sorted(Comparator.comparing(FineReasonRow::getReason))
                .peek(row -> {
                    row.setImposed(round2(row.getImposed()));
                    row.setCollected(round2(row.getCollected()));
                    row.setOutstanding(round2(row.getOutstanding()));
                })
                .collect(Collectors.toList()));
        return report;
    }

    // ─────────── waitlist ───────────

    public WaitlistReport waitlist(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);

        // The current length of a queue is not period-bound; the date range only
        // applies to the fulfilled entries used for the average wait time.
        List<Document> queueDocs = aggregate("waitlist", List.of(
                match(new Document("status", "WAITING")),
                new Document("$group", new Document("_id",
                        new Document("$toObjectId", "$bookId"))
                        .append("queueLength", new Document("$sum", 1))),
                new Document("$sort", new Document("queueLength", -1)),
                new Document("$limit", 10),
                new Document("$lookup", new Document("from", mongoTemplate.getCollectionName(Book.class))
                        .append("localField", "_id")
                        .append("foreignField", "_id")
                        .append("as", "book")),
                new Document("$unwind", "$book")
        ));

        List<Document> avgDocs = aggregate("waitlist", List.of(
                match(new Document("status", "FULFILLED")
                        .append("fulfilledAt", new Document("$ne", null))
                        .append("joinedAt", new Document("$gte", java.util.Date.from(range.from()))
                                .append("$lt", java.util.Date.from(range.toExclusive())))),
                new Document("$project", new Document("bookId", "$bookId")
                        .append("waitMs", new Document("$subtract", List.of("$fulfilledAt", "$joinedAt")))),
                new Document("$group", new Document("_id", "$bookId")
                        .append("avgWaitMs", new Document("$avg", "$waitMs")))
        ));

        Map<String, Double> avgByBook = new HashMap<>();
        for (Document doc : avgDocs) {
            avgByBook.put(String.valueOf(doc.get("_id")),
                    ((Number) doc.get("avgWaitMs")).doubleValue() / 3_600_000.0);
        }

        List<Document> overallDocs = aggregate("waitlist", List.of(
                match(new Document("status", "FULFILLED")
                        .append("fulfilledAt", new Document("$ne", null))
                        .append("joinedAt", new Document("$gte", java.util.Date.from(range.from()))
                                .append("$lt", java.util.Date.from(range.toExclusive())))),
                new Document("$group", new Document("_id", null)
                        .append("avgWaitMs", new Document("$avg",
                                new Document("$subtract", List.of("$fulfilledAt", "$joinedAt")))))
        ));
        Double overall = overallDocs.isEmpty() ? null
                : ((Number) overallDocs.get(0).get("avgWaitMs")).doubleValue() / 3_600_000.0;

        List<WaitlistRow> rows = new ArrayList<>();
        for (Document doc : queueDocs) {
            Document book = doc.get("book", Document.class);
            if (book == null) continue;
            WaitlistRow row = new WaitlistRow();
            row.setBookId(String.valueOf(doc.get("_id")));
            row.setTitle(book.getString("title"));
            row.setQueueLength(((Number) doc.get("queueLength")).longValue());
            Double avg = avgByBook.get(row.getBookId());
            row.setAverageWaitHours(avg == null ? null : round1(avg));
            rows.add(row);
        }

        WaitlistReport report = new WaitlistReport();
        report.setFrom(range.from());
        report.setTo(range.toExclusive());
        report.setOverallAverageWaitHours(overall == null ? null : round1(overall));
        report.setBooks(rows);
        return report;
    }

    // ─────────── inventory ───────────

    public InventoryReport inventory() {
        List<Document> docs = aggregate("copies", List.of(
                new Document("$group", new Document("_id", "$status")
                        .append("count", new Document("$sum", 1)))
        ));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (CopyStatus status : CopyStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        for (Document doc : docs) {
            byStatus.put(String.valueOf(doc.get("_id")), ((Number) doc.get("count")).longValue());
        }

        List<String> availableBookIds = mongoTemplate.findDistinct(
                Query.query(Criteria.where("status").is(CopyStatus.AVAILABLE)),
                "bookId", com.smartlib.model.Copy.class, String.class);
        Set<String> available = new HashSet<>(availableBookIds);

        List<BookBrief> zeroAvailable = bookRepository.findAll().stream()
                .filter(book -> !available.contains(book.getId()))
                .sorted(Comparator.comparing(Book::getTitle))
                .map(book -> {
                    BookBrief brief = new BookBrief();
                    brief.setBookId(book.getId());
                    brief.setTitle(book.getTitle());
                    brief.setAuthor(book.getAuthor());
                    brief.setSubject(book.getSubject());
                    brief.setIsbn(book.getIsbn());
                    brief.setTotalCopies(copyRepository.countByBookId(book.getId()));
                    brief.setAvailableCopies(copyRepository.countByBookIdAndStatus(
                            book.getId(), CopyStatus.AVAILABLE));
                    return brief;
                })
                .collect(Collectors.toList());

        InventoryReport report = new InventoryReport();
        report.setTotalCopies(copyRepository.count());
        report.setCopiesByStatus(byStatus);
        report.setBooksWithZeroAvailable(zeroAvailable);
        return report;
    }

    // ─────────── CSV export ───────────

    public CsvFile exportCsv(String type, LocalDate from, LocalDate to) {
        String normalized = type == null ? "" : type.toLowerCase();
        StringBuilder sb = new StringBuilder();
        String filename;
        switch (normalized) {
            case "overdue" -> {
                filename = "smartlib-overdue";
                appendRow(sb, "Student", "Book", "Due date", "Days late", "Accrued fine");
                for (OverdueRow row : overdue()) {
                    appendRow(sb, row.getStudentName(), row.getBookTitle(),
                            row.getDueDate() == null ? "" : row.getDueDate().toString(),
                            String.valueOf(row.getDaysLate()),
                            money(row.getAccruedFine()));
                }
            }
            case "popular" -> {
                DateRange range = range(from, to);
                filename = "smartlib-popular-books";
                appendRow(sb, "Rank", "Title", "Author", "Subject", "Borrows");
                for (PopularBook row : popularBooks(from, to, 50)) {
                    appendRow(sb, String.valueOf(row.getRank()), row.getTitle(), row.getAuthor(),
                            row.getSubject(), String.valueOf(row.getBorrowCount()));
                }
                sb.append("# period ").append(DAY.format(range.start()))
                        .append(" to ").append(DAY.format(range.end())).append('\n');
            }
            case "inventory" -> {
                filename = "smartlib-inventory";
                appendRow(sb, "Book", "Author", "Subject", "Total copies", "Available copies");
                for (BookBrief brief : inventory().getBooksWithZeroAvailable()) {
                    appendRow(sb, brief.getTitle(), brief.getAuthor(), brief.getSubject(),
                            String.valueOf(brief.getTotalCopies()),
                            String.valueOf(brief.getAvailableCopies()));
                }
            }
            case "fines" -> {
                DateRange range = range(from, to);
                filename = "smartlib-fines";
                appendRow(sb, "Reason", "Count", "Imposed", "Collected", "Outstanding");
                FinesReport report = fines(from, to);
                for (FineReasonRow row : report.getByReason()) {
                    appendRow(sb, row.getReason(), String.valueOf(row.getCount()),
                            money(row.getImposed()), money(row.getCollected()), money(row.getOutstanding()));
                }
                appendRow(sb, "TOTAL", "",
                        money(report.getTotalImposed()), money(report.getTotalCollected()),
                        money(report.getTotalOutstanding()));
                sb.append("# period ").append(DAY.format(range.start()))
                        .append(" to ").append(DAY.format(range.end())).append('\n');
            }
            default -> throw new IllegalArgumentException(
                    "Unknown export type. Use overdue, popular, inventory or fines.");
        }
        return new CsvFile(filename + "-" + DAY.format(LocalDate.now(ZoneOffset.UTC)) + ".csv", sb.toString());
    }

    public record CsvFile(String filename, String content) {
    }

    private void appendRow(StringBuilder sb, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(values[i]));
        }
        sb.append('\n');
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String money(double amount) {
        return String.valueOf(round2(amount));
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    // ─────────── aggregation helpers ───────────

    private List<Document> aggregate(String collection, List<Document> stages) {
        List<AggregationOperation> operations = stages.stream()
                .map(stage -> (AggregationOperation) context -> stage)
                .collect(Collectors.toList());
        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, collection, Document.class).getMappedResults();
    }

    private Document match(Document criteria) {
        return new Document("$match", criteria);
    }
}

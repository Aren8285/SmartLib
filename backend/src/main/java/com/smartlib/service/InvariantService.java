package com.smartlib.service;

import com.smartlib.dto.InvariantViolation;
import com.smartlib.model.Copy;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.WaitlistStatus;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Consistency checks for the copy/loan/waitlist triangle. Used by the dev
 * endpoint, by repair tooling and by a daily scheduled self-check that logs
 * warnings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvariantService {

    private final CopyRepository copyRepository;
    private final LoanRepository loanRepository;
    private final WaitlistRepository waitlistRepository;
    private final MongoTemplate mongoTemplate;
    private final WaitlistService waitlistService;

    public List<InvariantViolation> check() {
        List<InvariantViolation> violations = new ArrayList<>();

        // Every RESERVED copy has exactly one READY_FOR_PICKUP loan pointing at it.
        for (Copy copy : copyRepository.findByStatus(CopyStatus.RESERVED)) {
            long ready = loanRepository.countByCopyIdAndStatus(copy.getId(), LoanStatus.READY_FOR_PICKUP);
            if (ready != 1) {
                violations.add(new InvariantViolation("RESERVED_COPY",
                        "Copy " + copy.getId() + " is RESERVED but has " + ready
                                + " READY_FOR_PICKUP loan(s)."));
            }
        }

        // Every ISSUED copy has exactly one ISSUED/OVERDUE loan pointing at it.
        for (Copy copy : copyRepository.findByStatus(CopyStatus.ISSUED)) {
            long active = loanRepository.countByCopyIdAndStatusIn(copy.getId(),
                    List.of(LoanStatus.ISSUED, LoanStatus.OVERDUE));
            if (active != 1) {
                violations.add(new InvariantViolation("ISSUED_COPY",
                        "Copy " + copy.getId() + " is ISSUED but has " + active
                                + " ISSUED/OVERDUE loan(s)."));
            }
        }

        // No student has two active loans or queue entries for the same book.
        List<Document> duplicates = aggregate("loans", List.of(
                new Document("$match", new Document("status", new Document("$in",
                        List.of("WAITLISTED", "READY_FOR_PICKUP", "ISSUED", "OVERDUE")))),
                new Document("$group", new Document("_id",
                        new Document("studentId", "$studentId").append("bookId", "$bookId"))
                        .append("count", new Document("$sum", 1))),
                new Document("$match", new Document("count", new Document("$gt", 1)))
        ));
        for (Document doc : duplicates) {
            Document key = doc.get("_id", Document.class);
            violations.add(new InvariantViolation("DUPLICATE_ACTIVE_LOAN",
                    "Student " + (key == null ? "?" : key.get("studentId"))
                            + " has " + doc.get("count") + " active loan(s) for book "
                            + (key == null ? "?" : key.get("bookId")) + "."));
        }

        // No WAITING entry exists for a book that has an AVAILABLE copy (stuck queue).
        Set<String> booksWithWaiters = new HashSet<>();
        waitlistRepository.findByStatus(WaitlistStatus.WAITING)
                .forEach(entry -> booksWithWaiters.add(entry.getBookId()));
        for (String bookId : booksWithWaiters) {
            long available = copyRepository.countByBookIdAndStatus(bookId, CopyStatus.AVAILABLE);
            if (available > 0) {
                violations.add(new InvariantViolation("STUCK_QUEUE",
                        "Book " + bookId + " has " + available
                                + " AVAILABLE copy/copies while students are waiting."));
            }
        }
        return violations;
    }

    /**
     * Repairs stuck queues by releasing available copies to waiting students.
     * Returns the number of copies released.
     */
    public int repairStuckQueues() {
        int repaired = 0;
        Set<String> booksWithWaiters = new HashSet<>();
        waitlistRepository.findByStatus(WaitlistStatus.WAITING)
                .forEach(entry -> booksWithWaiters.add(entry.getBookId()));
        for (String bookId : booksWithWaiters) {
            while (waitlistRepository.countByBookIdAndStatus(bookId, WaitlistStatus.WAITING) > 0) {
                List<Copy> available = copyRepository.findByBookIdAndStatus(bookId, CopyStatus.AVAILABLE);
                if (available.isEmpty()) {
                    break;
                }
                for (Copy copy : available) {
                    if (waitlistRepository.countByBookIdAndStatus(bookId, WaitlistStatus.WAITING) == 0) {
                        break;
                    }
                    if (waitlistService.releaseCopy(bookId, copy.getId()) != null) {
                        repaired++;
                    }
                }
            }
        }
        if (repaired > 0) {
            log.info("Repaired {} stuck queue copy/copies.", repaired);
        }
        return repaired;
    }

    /** Logs a warning when the daily self-check finds violations. */
    public void logCheck() {
        List<InvariantViolation> violations = check();
        if (violations.isEmpty()) {
            log.info("Invariant check: no violations.");
        } else {
            violations.forEach(v -> log.warn("Invariant violation [{}]: {}", v.getType(), v.getDetails()));
        }
    }

    private List<Document> aggregate(String collection, List<Document> stages) {
        List<AggregationOperation> operations = stages.stream()
                .map(stage -> (AggregationOperation) context -> stage)
                .toList();
        return mongoTemplate.aggregate(Aggregation.newAggregation(operations), collection, Document.class)
                .getMappedResults();
    }
}

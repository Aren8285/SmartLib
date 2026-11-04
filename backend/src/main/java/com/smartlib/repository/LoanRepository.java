package com.smartlib.repository;

import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends MongoRepository<Loan, String> {

    List<Loan> findByStudentIdOrderByRequestDateDesc(String studentId);

    List<Loan> findByStatus(LoanStatus status);

    /** Dashboard counts for a set of statuses. */
    long countByStatusIn(List<LoanStatus> statuses);

    /** Ascending by a chosen field (oldest-first action lists). */
    List<Loan> findByStatus(LoanStatus status, Sort sort);

    Optional<Loan> findByStudentIdAndBookIdAndStatusIn(String studentId, String bookId, List<LoanStatus> statuses);

    long countByStudentIdAndStatusIn(String studentId, List<LoanStatus> statuses);

    /** Expiry job: READY_FOR_PICKUP loans whose pickup deadline has passed. */
    List<Loan> findByStatusAndPickupDeadlineBefore(LoanStatus status, Instant deadline);

    /** Overdue job: ISSUED loans past their due date. */
    List<Loan> findByStatusAndDueDateBefore(LoanStatus status, Instant now);

    /** One-time pickup reminders: not yet sent, deadline < 6h away. */
    List<Loan> findByStatusAndReminderSentFalseAndPickupDeadlineBetween(
            LoanStatus status, Instant from, Instant to);

    /** One-time due reminders: not yet sent, due within 24h (ISSUED only; overdue handled separately). */
    List<Loan> findByStatusAndDueReminderSentFalseAndDueDateBetween(
            LoanStatus status, Instant from, Instant to);

    /** Reading history & similar-search queries. */
    List<Loan> findByStudentIdAndStatusIn(String studentId, List<LoanStatus> statuses);

    List<Loan> findByBookIdAndStatusIn(String bookId, List<LoanStatus> statuses);

    List<Loan> findByCopyIdAndStatusIn(String copyId, List<LoanStatus> statuses);

    /** Books a set of students read (similar-reader signal uses whole set lookups). */
    List<Loan> findByStudentIdInAndStatusIn(List<String> studentIds, List<LoanStatus> statuses);

    long countByBookIdAndStatusIn(String bookId, List<LoanStatus> statuses);

    boolean existsByBookIdAndStatusIn(String bookId, List<LoanStatus> statuses);

    /** Ever borrowed (any status) - popularity count. */
    long countByBookId(String bookId);

    long countByStudentIdAndStatusInAndBookId(String studentId, List<LoanStatus> statuses, String bookId);

    /** Borrow count across the library (popular books). */
    @Query("{ 'status': { $in: ?0 } }")
    List<Loan> findAllInStatuses(List<LoanStatus> statuses);
}

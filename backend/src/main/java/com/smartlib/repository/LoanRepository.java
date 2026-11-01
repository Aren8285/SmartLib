package com.smartlib.repository;

import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends MongoRepository<Loan, String> {

    List<Loan> findByStudentIdOrderByRequestDateDesc(String studentId);

    List<Loan> findByStatus(LoanStatus status);

    Optional<Loan> findByStudentIdAndBookIdAndStatusIn(String studentId, String bookId, List<LoanStatus> statuses);

    long countByStudentIdAndStatusIn(String studentId, List<LoanStatus> statuses);

    /** Expiry job: READY_FOR_PICKUP loans whose pickup deadline has passed. */
    List<Loan> findByStatusAndPickupDeadlineBefore(LoanStatus status, Instant deadline);

    /** Overdue job: ISSUED loans past their due date. */
    List<Loan> findByStatusAndDueDateBefore(LoanStatus status, Instant now);
}

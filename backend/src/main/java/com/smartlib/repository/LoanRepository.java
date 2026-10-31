package com.smartlib.repository;

import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends MongoRepository<Loan, String> {
    List<Loan> findByStudentIdOrderByRequestDateDesc(String studentId);
    List<Loan> findByStatus(LoanStatus status);
    Optional<Loan> findByStudentIdAndBookIdAndStatusIn(String studentId, String bookId, List<LoanStatus> statuses);
}


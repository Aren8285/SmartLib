package com.smartlib.repository;

import com.smartlib.model.WaitlistEntry;
import com.smartlib.model.WaitlistStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends MongoRepository<WaitlistEntry, String> {

    List<WaitlistEntry> findByBookIdAndStatusOrderByJoinedAtAsc(String bookId, WaitlistStatus status);

    long countByBookIdAndStatus(String bookId, WaitlistStatus status);

    long countByBookIdAndStatusAndJoinedAtLessThan(String bookId, WaitlistStatus status, Instant joinedAt);

    Optional<WaitlistEntry> findByLoanId(String loanId);

    boolean existsByStudentIdAndBookIdAndStatus(String studentId, String bookId, WaitlistStatus status);

    boolean existsByBookIdAndStatus(String bookId, WaitlistStatus status);

    Optional<WaitlistEntry> findFirstByBookIdAndStatusOrderByJoinedAtAsc(String bookId, WaitlistStatus status);

    List<WaitlistEntry> findByStudentIdAndStatus(String studentId, WaitlistStatus status);

    List<WaitlistEntry> findByStatus(WaitlistStatus status);

    long countByStatus(WaitlistStatus status);
}

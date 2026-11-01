package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.model.Copy;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.WaitlistEntry;
import com.smartlib.model.WaitlistStatus;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * FIFO waitlist. Queue positions are always computed from joinedAt order
 * among WAITING entries; they are never stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final CopyRepository copyRepository;
    private final AppProperties app;

    public WaitlistEntry join(String bookId, String studentId, String loanId) {
        return waitlistRepository.save(WaitlistEntry.builder()
                .bookId(bookId)
                .studentId(studentId)
                .loanId(loanId)
                .joinedAt(Instant.now())
                .status(WaitlistStatus.WAITING)
                .build());
    }

    public long countWaiting(String bookId) {
        return waitlistRepository.countByBookIdAndStatus(bookId, WaitlistStatus.WAITING);
    }

    public int queuePosition(WaitlistEntry entry) {
        long ahead = waitlistRepository.countByBookIdAndStatusAndJoinedAtLessThan(
                entry.getBookId(), WaitlistStatus.WAITING, entry.getJoinedAt());
        return (int) ahead + 1;
    }

    public Optional<WaitlistEntry> findEntryByLoan(String loanId) {
        return waitlistRepository.findByLoanId(loanId);
    }

    public void cancelByLoanId(String loanId) {
        waitlistRepository.findByLoanId(loanId).ifPresent(entry -> {
            entry.setStatus(WaitlistStatus.REMOVED);
            waitlistRepository.save(entry);
        });
    }

    public List<WaitlistEntry> queueFor(String bookId) {
        return waitlistRepository.findByBookIdAndStatusOrderByJoinedAtAsc(bookId, WaitlistStatus.WAITING);
    }

    /**
     * Shared "release copy" routine (used by return, pickup expiry and
     * cancel-while-READY_FOR_PICKUP):
     * - if someone is WAITING, the oldest entry is promoted: the copy stays
     *   RESERVED for them, their loan goes WAITLISTED -> READY_FOR_PICKUP with
     *   a fresh pickup deadline;
     * - if the queue is empty, the copy goes back to AVAILABLE.
     * Because each promoted loan keeps the same copy, chaining works: if the
     * promoted student lets the deadline expire, the next release call
     * promotes the next WAITING student.
     *
     * @return the promoted loan, or null if nobody was promoted.
     */
    public Loan releaseCopy(String bookId, String copyId) {
        if (copyId == null) {
            return null;
        }
        Optional<WaitlistEntry> nextOpt = waitlistRepository.findFirstByBookIdAndStatusOrderByJoinedAtAsc(bookId, WaitlistStatus.WAITING);
        if (nextOpt.isEmpty()) {
            copyRepository.findById(copyId).ifPresent(copy -> {
                copy.setStatus(CopyStatus.AVAILABLE);
                copyRepository.save(copy);
            });
            log.info("Released copy {} of book {} back to AVAILABLE (no waiting students).", copyId, bookId);
            return null;
        }

        WaitlistEntry next = nextOpt.get();
        next.setStatus(WaitlistStatus.FULFILLED);
        waitlistRepository.save(next);

        Loan promoted = loanRepository.findById(next.getLoanId()).orElse(null);
        if (promoted == null || promoted.getStatus() != LoanStatus.WAITLISTED) {
            log.warn("Waitlist entry {} has no WAITLISTED loan behind it; copy {} stays reserved.", next.getId(), copyId);
            return null;
        }

        Instant now = Instant.now();
        promoted.setStatus(LoanStatus.READY_FOR_PICKUP);
        promoted.setCopyId(copyId);
        promoted.setPickupDeadline(now.plus(app.getPickupDeadlineHours(), ChronoUnit.HOURS));
        loanRepository.save(promoted);

        copyRepository.findById(copyId).ifPresent(copy -> {
            if (copy.getStatus() != CopyStatus.RESERVED) {
                copy.setStatus(CopyStatus.RESERVED);
                copyRepository.save(copy);
            }
        });

        log.info("Promoted waitlist entry {} : loan {} is now READY_FOR_PICKUP, holding copy {}.", next.getId(), promoted.getId(), copyId);
        return promoted;
    }
}

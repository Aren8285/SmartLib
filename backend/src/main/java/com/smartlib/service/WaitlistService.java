package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * FIFO waitlist. Queue positions are always computed from joinedAt order among
 * WAITING entries; they are never stored.
 *
 * <p>Promotion claims the oldest entry with a single atomic findAndModify
 * (WAITING -> FULFILLED). Two simultaneous returns/copy additions therefore
 * cannot promote the same entry twice: the loser gets the next entry or simply
 * leaves the copy AVAILABLE.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final CopyRepository copyRepository;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;
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
     * Shared "release copy" routine (used by return, pickup expiry,
     * cancel-while-READY_FOR_PICKUP, repaired copy and added copies):
     * <ul>
     *   <li>atomically claims the oldest WAITING entry (-> FULFILLED, fulfilledAt),</li>
     *   <li>atomically flips its loan WAITLISTED -> READY_FOR_PICKUP with the copy,</li>
     *   <li>if the loan cannot be promoted (stale entry) it is skipped and the next
     *       entry is considered,</li>
     *   <li>when nobody can be promoted the copy goes AVAILABLE.</li>
     * </ul>
     *
     * @return the promoted loan, or null if nobody was promoted.
     */
    public Loan releaseCopy(String bookId, String copyId) {
        if (copyId == null) {
            return null;
        }
        Instant now = Instant.now();
        while (true) {
            WaitlistEntry claimed = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("bookId").is(bookId)
                                    .and("status").is(WaitlistStatus.WAITING))
                            .with(Sort.by(Sort.Direction.ASC, "joinedAt")),
                    new Update().set("status", WaitlistStatus.FULFILLED)
                            .set("fulfilledAt", now),
                    FindAndModifyOptions.options().returnNew(true),
                    WaitlistEntry.class);

            if (claimed == null) {
                copyRepository.findById(copyId).ifPresent(copy -> {
                    if (copy.getStatus() != CopyStatus.AVAILABLE) {
                        copy.setStatus(CopyStatus.AVAILABLE);
                        copyRepository.save(copy);
                    }
                });
                log.info("Released copy {} of book {} back to AVAILABLE (no waiting students).", copyId, bookId);
                return null;
            }

            Instant deadline = now.plus(app.getPickupDeadlineHours(), ChronoUnit.HOURS);
            Loan promoted = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(claimed.getLoanId())
                            .and("status").is(LoanStatus.WAITLISTED)),
                    new Update().set("status", LoanStatus.READY_FOR_PICKUP)
                            .set("copyId", copyId)
                            .set("pickupDeadline", deadline),
                    FindAndModifyOptions.options().returnNew(true),
                    Loan.class);

            if (promoted == null) {
                log.warn("Waitlist entry {} has no WAITLISTED loan behind it; trying the next entry.",
                        claimed.getId());
                continue;
            }

            copyRepository.findById(copyId).ifPresent(copy -> {
                if (copy.getStatus() != CopyStatus.RESERVED) {
                    copy.setStatus(CopyStatus.RESERVED);
                    copyRepository.save(copy);
                }
            });

            String bookTitle = bookRepository.findById(promoted.getBookId())
                    .map(Book::getTitle).orElse("your book");
            notificationService.create(promoted.getStudentId(), NotificationType.PROMOTED,
                    "It's your turn: pick up " + bookTitle,
                    "You moved up the queue for \"" + bookTitle + "\". The copy is held for you until "
                            + java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
                                    .withZone(java.time.ZoneId.systemDefault())
                                    .format(deadline) + ".",
                    promoted.getId());

            log.info("Promoted waitlist entry {}: loan {} is now READY_FOR_PICKUP, holding copy {}.",
                    claimed.getId(), promoted.getId(), copyId);
            return promoted;
        }
    }
}

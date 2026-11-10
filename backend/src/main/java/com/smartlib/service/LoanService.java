package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.FineDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.PageResponse;
import com.smartlib.dto.ReturnLoanDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loan state machine with optimistic, status-gated writes.
 *
 * <p>Every action that changes state performs a conditional update
 * ({@code _id + expected status}) and treats a zero match count as a 409.
 * That makes double-clicks, duplicate requests and two scheduler instances
 * harmless: only the first writer transitions the loan (and therefore only it
 * awards points or creates a fine).</p>
 *
 * <pre>
 * READY_FOR_PICKUP -> ISSUED | EXPIRED | CANCELLED
 * ISSUED           -> OVERDUE | REFLECTION_PENDING (on-time) | FINE_PENDING (late/damaged)
 * FINE_PENDING     -> REFLECTION_PENDING (late/damaged paid) | CLOSED (lost paid)
 * REFLECTION_PENDING -> CLOSED (reflection reviewed/skipped)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    static final List<LoanStatus> ACTIVE_LOAN_STATUSES =
            List.of(LoanStatus.READY_FOR_PICKUP, LoanStatus.ISSUED, LoanStatus.OVERDUE);
    static final List<LoanStatus> DUPLICATE_CHECK_STATUSES =
            List.of(LoanStatus.WAITLISTED, LoanStatus.READY_FOR_PICKUP, LoanStatus.ISSUED, LoanStatus.OVERDUE);

    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.systemDefault());

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final WaitlistRepository waitlistRepository;
    private final FineRepository fineRepository;
    private final ReflectionRepository reflectionRepository;
    private final MongoTemplate mongoTemplate;
    private final WaitlistService waitlistService;
    private final FineService fineService;
    private final NotificationService notificationService;
    private final AppProperties app;

    // ────────────────────────────── 1. REQUEST ──────────────────────────────

    public LoanResponse requestLoan(String studentId, LoanRequestDto dto) {
        User student = requireStudent(studentId);
        Book book = requireBook(dto.getBookId());

        loanRepository.findByStudentIdAndBookIdAndStatusIn(
                        studentId, dto.getBookId(), DUPLICATE_CHECK_STATUSES)
                .ifPresent(l -> {
                    throw new BusinessRuleException(
                            "You already have an active loan for this book (status: " + l.getStatus() + ").");
                });
        if (waitlistRepository.existsByStudentIdAndBookIdAndStatus(
                studentId, dto.getBookId(), WaitlistStatus.WAITING)) {
            throw new BusinessRuleException(
                    "You already have an active loan for this book (status: WAITLISTED).");
        }

        if (fineRepository.countByStudentIdAndStatus(studentId, FineStatus.PENDING) > 0) {
            throw new BusinessRuleException("Pay your pending fines first.");
        }

        long active = loanRepository.countByStudentIdAndStatusIn(studentId, ACTIVE_LOAN_STATUSES);
        if (active >= app.getMaxActiveLoans()) {
            throw new BusinessRuleException(
                    "Active loan limit reached (max " + app.getMaxActiveLoans() + ").");
        }

        Instant now = Instant.now();

        long waiting = waitlistService.countWaiting(book.getId());
        if (waiting > 0) {
            return addToWaitlist(student, book, now, waiting);
        }

        Copy reserved = tryReserveCopy(book.getId());
        if (reserved != null) {
            Instant deadline = now.plus(app.getPickupDeadlineHours(), ChronoUnit.HOURS);
            Loan loan = loanRepository.save(Loan.builder()
                    .studentId(student.getId())
                    .bookId(book.getId())
                    .copyId(reserved.getId())
                    .status(LoanStatus.READY_FOR_PICKUP)
                    .requestDate(now)
                    .pickupDeadline(deadline)
                    .build());
            LoanResponse response = toResponse(loan, book.getTitle(), student.getName());
            response.setMessage("Copy reserved. Pick up before " + DEADLINE_FORMAT.format(deadline) + ".");
            return response;
        }

        return addToWaitlist(student, book, now, 0);
    }

    private LoanResponse addToWaitlist(User student, Book book, Instant now, long currentQueueSize) {
        if (currentQueueSize >= app.getMaxWaitlistEntries()) {
            throw new BusinessRuleException(
                    "The waitlist for this book is full (max " + app.getMaxWaitlistEntries() + ").");
        }
        Loan loan = loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .status(LoanStatus.WAITLISTED)
                .copyId(null)
                .requestDate(now)
                .build());
        waitlistService.join(book.getId(), student.getId(), loan.getId());
        LoanResponse response = toResponse(loan, book.getTitle(), student.getName());
        response.setMessage("You are #" + response.getQueuePosition() + " in the queue for this book.");
        return response;
    }

    private Copy tryReserveCopy(String bookId) {
        Query query = Query.query(Criteria.where("bookId").is(bookId)
                .and("status").is(CopyStatus.AVAILABLE))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        Update update = new Update().set("status", CopyStatus.RESERVED);
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                Copy.class);
    }

    // ────────────────────────────── 2. ISSUE ──────────────────────────────

    public LoanResponse issueLoan(String loanId) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.READY_FOR_PICKUP) {
            throw new BusinessRuleException(
                    "Only READY_FOR_PICKUP loans can be issued. Current status: " + loan.getStatus());
        }
        Instant now = Instant.now();
        if (loan.getPickupDeadline() == null || now.isAfter(loan.getPickupDeadline())) {
            throw new BusinessRuleException("Pickup deadline passed");
        }
        String copyId = loan.getCopyId();
        Copy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found: " + copyId));
        if (copy.getStatus() != CopyStatus.RESERVED) {
            throw new BusinessRuleException("The reserved copy is no longer available.");
        }

        Instant dueDate = now.plus(app.getLoanDays(), ChronoUnit.DAYS);
        Loan updated = transition(loan, List.of(LoanStatus.READY_FOR_PICKUP),
                new Update().set("status", LoanStatus.ISSUED)
                        .set("issueDate", now)
                        .set("dueDate", dueDate));
        if (updated == null) {
            throw new BusinessRuleException(
                    "This loan was already issued or changed. Refresh and try again.");
        }

        copy.setStatus(CopyStatus.ISSUED);
        copyRepository.save(copy);

        notificationService.create(updated.getStudentId(), NotificationType.ISSUED,
                "Book issued",
                "\"" + titleOf(updated.getBookId()).getTitle() + "\" is yours. Due back by "
                        + DEADLINE_FORMAT.format(dueDate) + ".",
                updated.getId());

        LoanResponse response = toResponse(updated, null, null);
        response.setMessage("Issued. Due back by " + DEADLINE_FORMAT.format(response.getDueDate()) + ".");
        return response;
    }

    // ────────────────────────────── 3. RETURN ──────────────────────────────

    /**
     * Return handling, including the damaged-book variant: damage turns the copy
     * DAMAGED (kept out of circulation) and creates one fine with the late amount
     * folded in. Only the winning conditional update performs side effects.
     */
    public LoanResponse returnLoan(String loanId, ReturnLoanDto dto) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException(
                    "Only ISSUED or OVERDUE loans can be returned. Current status: " + loan.getStatus());
        }
        boolean damaged = dto != null && dto.getBookCondition() == BookCondition.DAMAGED;

        Instant now = Instant.now();
        boolean onTime = loan.getDueDate() == null || !now.isAfter(loan.getDueDate());
        long daysLate = onTime ? 0 : (long) Math.max(1, Math.ceil(
                Duration.between(loan.getDueDate(), now).getSeconds() / 86400.0));

        LoanStatus target = damaged ? LoanStatus.FINE_PENDING
                : onTime ? LoanStatus.REFLECTION_PENDING : LoanStatus.FINE_PENDING;

        Loan updated = transition(loan, List.of(LoanStatus.ISSUED, LoanStatus.OVERDUE),
                new Update().set("status", target).set("returnDate", now));
        if (updated == null) {
            throw new BusinessRuleException(
                    "This loan has already been returned or changed. Refresh and try again.");
        }
        updated.setReturnDate(now);

        LoanResponse response;
        if (damaged) {
            copyRepository.findById(updated.getCopyId()).ifPresent(copy -> {
                copy.setStatus(CopyStatus.DAMAGED);
                copyRepository.save(copy);
            });

            double damagedAmount = app.getDamagedBookFine();
            double lateAmount = daysLate * app.getFinePerDay();
            Fine fine = fineService.createDamaged(updated.getStudentId(), loanId, damagedAmount, lateAmount);
            attachFine(updated, fine);
            loan.setFineId(fine.getId());

            notificationService.create(updated.getStudentId(), NotificationType.FINE_IMPOSED,
                    "Fine imposed",
                    "The copy of \"" + titleOf(updated.getBookId()).getTitle()
                            + "\" was returned damaged. Pending fine: " + money(fine.getAmount()) + ".",
                    updated.getId());

            response = toResponse(updated, null, null);
            response.setFine(fineService.toDto(fine));
            response.setMessage("Returned DAMAGED. Pending fine: " + money(fine.getAmount())
                    + (daysLate > 0 ? " (damaged-book " + money(damagedAmount) + " + late "
                       + money(lateAmount) + ")" : "")
                    + ". Pay it to move on to your reflection.");
        } else if (onTime) {
            awardPoints(updated.getStudentId(), app.getOnTimeReturnPoints());
            releaseCopyAfterReturn(updated);
            response = toResponse(updated, null, null);
            response.setMessage("Returned on time. +" + app.getOnTimeReturnPoints()
                    + " points! Write a reflection to earn " + app.getReflectionPoints() + " more.");
        } else {
            double amount = daysLate * app.getFinePerDay();
            Fine fine = fineService.create(updated.getStudentId(), loanId, amount, FineReason.LATE_RETURN);
            attachFine(updated, fine);
            loan.setFineId(fine.getId());
            releaseCopyAfterReturn(updated);

            notificationService.create(updated.getStudentId(), NotificationType.FINE_IMPOSED,
                    "Fine imposed",
                    "\"" + titleOf(updated.getBookId()).getTitle() + "\" was " + daysLate
                            + " day(s) late. Pending fine: " + money(amount)
                            + ". Pay it to move on to your reflection.",
                    updated.getId());

            response = toResponse(updated, null, null);
            response.setMessage("Late return (" + daysLate + " day(s) overdue). Pending fine: "
                    + money(amount) + ". Pay it to move on to your reflection.");
        }
        return response;
    }

    /** Copy release AFTER the loan row is persisted, so FIFO promotion cannot race with bookkeeping. */
    private void releaseCopyAfterReturn(Loan loan) {
        waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
    }

    // ─────────── 4. LOST ───────────

    public LoanResponse lostLoan(String loanId) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException(
                    "Only ISSUED or OVERDUE loans can be marked lost. Current status: " + loan.getStatus());
        }
        String copyId = loan.getCopyId();
        Copy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found: " + copyId));

        Loan updated = transition(loan, List.of(LoanStatus.ISSUED, LoanStatus.OVERDUE),
                new Update().set("status", LoanStatus.FINE_PENDING));
        if (updated == null) {
            throw new BusinessRuleException(
                    "This loan has already been returned or changed. Refresh and try again.");
        }

        // The copy is NOT released to the queue.
        copy.setStatus(CopyStatus.LOST);
        copyRepository.save(copy);

        Fine fine = fineService.create(updated.getStudentId(), loanId, app.getLostBookFine(), FineReason.LOST_BOOK);
        attachFine(updated, fine);
        loan.setFineId(fine.getId());

        notificationService.create(updated.getStudentId(), NotificationType.FINE_IMPOSED,
                "Fine imposed",
                "\"" + titleOf(updated.getBookId()).getTitle()
                        + "\" was marked lost. Pending fine: " + money(app.getLostBookFine()) + ".",
                updated.getId());

        log.info("Loan {} marked lost; copy {} is lost (not released to the queue); pending fine {} created.",
                loan.getId(), copy.getId(), fine.getId());
        LoanResponse response = toResponse(updated, null, null);
        response.setFine(fineService.toDto(fine));
        response.setMessage("Marked lost. Pending fine: " + money(app.getLostBookFine())
                + ". Pay it to close the loan.");
        return response;
    }

    // ─────────── 5. CANCEL ───────────

    public LoanResponse cancelLoan(String loanId, String studentId) {
        Loan loan = requireLoan(loanId);
        requireOwnership(loan, studentId);
        LoanStatus status = loan.getStatus();

        if (status == LoanStatus.WAITLISTED) {
            Loan updated = transition(loan, List.of(LoanStatus.WAITLISTED),
                    new Update().set("status", LoanStatus.CANCELLED));
            if (updated == null) {
                throw new BusinessRuleException("This request was already cancelled or changed.");
            }
            waitlistService.cancelByLoanId(updated.getId());
            loan.setStatus(LoanStatus.CANCELLED);
            LoanResponse response = toResponse(updated, null, null);
            response.setMessage("Request cancelled. You left the queue.");
            return response;
        }

        if (status == LoanStatus.READY_FOR_PICKUP) {
            Loan updated = transition(loan, List.of(LoanStatus.READY_FOR_PICKUP),
                    new Update().set("status", LoanStatus.CANCELLED));
            if (updated == null) {
                throw new BusinessRuleException("This request was already cancelled or changed.");
            }
            waitlistService.releaseCopy(updated.getBookId(), updated.getCopyId());
            LoanResponse response = toResponse(updated, null, null);
            response.setMessage("Request cancelled. The copy moves to the next student in the queue.");
            return response;
        }

        throw new BusinessRuleException("Loans in status " + status + " cannot be cancelled.");
    }

    // ─────────── 6. SKIP REFLECTION ───────────

    public LoanResponse skipReflection(String loanId, String studentId) {
        Loan loan = requireLoan(loanId);
        requireOwnership(loan, studentId);
        if (loan.getStatus() != LoanStatus.REFLECTION_PENDING) {
            throw new BusinessRuleException(
                    "Only REFLECTION_PENDING loans can skip the reflection. Current status: " + loan.getStatus());
        }
        if (reflectionRepository.existsByLoanId(loanId)) {
            throw new BusinessRuleException(
                    "You already submitted a reflection for this loan; it can no longer be skipped.");
        }
        Loan updated = transition(loan, List.of(LoanStatus.REFLECTION_PENDING),
                new Update().set("status", LoanStatus.CLOSED));
        if (updated == null) {
            throw new BusinessRuleException("This loan was already closed or changed.");
        }
        LoanResponse response = toResponse(updated, null, null);
        response.setMessage("Reflection skipped. The loan is now CLOSED (no reflection points awarded).");
        return response;
    }

    // ─────────── 7. SELF-SERVICE RENEWAL ───────────

    /**
     * Extends an issued loan when the copy is not needed by another reader.
     * Renewal is deliberately blocked once a queue exists so self-service never
     * jumps ahead of a student who is waiting.
     */
    public LoanResponse renewLoan(String loanId, String studentId) {
        Loan loan = requireLoan(loanId);
        requireOwnership(loan, studentId);
        if (loan.getStatus() != LoanStatus.ISSUED) {
            throw new BusinessRuleException(
                    "Only issued loans can be renewed. Current status: " + loan.getStatus() + ".");
        }
        Instant now = Instant.now();
        if (loan.getDueDate() == null || !now.isBefore(loan.getDueDate())) {
            throw new BusinessRuleException("Overdue loans cannot be renewed; please return the book.");
        }
        if (waitlistService.countWaiting(loan.getBookId()) > 0) {
            throw new BusinessRuleException(
                    "Another student is waiting for this book. Please return it so the queue can move.");
        }
        int used = loan.getRenewalCount();
        if (used >= app.getMaxRenewals()) {
            throw new BusinessRuleException(
                    "Renewal limit reached (" + app.getMaxRenewals() + "). Please return the book.");
        }

        Instant newDueDate = loan.getDueDate().plus(app.getLoanDays(), ChronoUnit.DAYS);
        Criteria renewalGuard = used == 0
                ? new Criteria().orOperator(
                        Criteria.where("renewalCount").is(0),
                        Criteria.where("renewalCount").exists(false))
                : Criteria.where("renewalCount").is(used);
        Loan updated = transition(loan, List.of(LoanStatus.ISSUED),
                new Update().set("dueDate", newDueDate)
                        .set("renewalCount", used + 1)
                        .set("dueReminderSent", false), renewalGuard);
        if (updated == null) {
            throw new BusinessRuleException(
                    "This loan was already renewed or changed. Refresh and try again.");
        }

        String title = titleOf(updated.getBookId()).getTitle();
        notificationService.create(updated.getStudentId(), NotificationType.RENEWED,
                "Loan renewed",
                "\"" + title + "\" is renewed. The new due date is "
                        + DEADLINE_FORMAT.format(newDueDate) + ".",
                updated.getId());

        LoanResponse response = toResponse(updated, null, null);
        response.setMessage("Renewed. Due back by " + DEADLINE_FORMAT.format(newDueDate) + ".");
        return response;
    }

    // ─────────── 8. SCHEDULED JOBS (+ dev run-jobs) ───────────

    public int runPickupExpiryJob() {
        Instant now = Instant.now();
        List<Loan> dueToExpire = loanRepository.findByStatusAndPickupDeadlineBefore(
                LoanStatus.READY_FOR_PICKUP, now);
        int count = 0;
        for (Loan candidate : dueToExpire) {
            try {
                Loan loan = transition(candidate, List.of(LoanStatus.READY_FOR_PICKUP),
                        new Update().set("status", LoanStatus.EXPIRED));
                if (loan == null) {
                    continue; // another scheduler instance already handled it
                }
                waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
                notificationService.create(loan.getStudentId(), NotificationType.EXPIRED,
                        "Pickup window expired",
                        "You did not pick up \"" + titleOf(loan.getBookId()).getTitle()
                                + "\" before the deadline, so the copy moved to the next student.",
                        loan.getId());
                log.info("Pickup deadline passed for loan {} (book {}, copy {}): now EXPIRED, copy released.",
                        loan.getId(), loan.getBookId(), loan.getCopyId());
                count++;
            } catch (Exception e) {
                log.error("Pickup expiry job failed for loan {}: {}", candidate.getId(), e.getMessage(), e);
            }
        }
        return count;
    }

    public int runOverdueJob() {
        Instant now = Instant.now();
        List<Loan> dueToOverdue = loanRepository.findByStatusAndDueDateBefore(LoanStatus.ISSUED, now);
        int count = 0;
        for (Loan candidate : dueToOverdue) {
            try {
                Loan loan = transition(candidate, List.of(LoanStatus.ISSUED),
                        new Update().set("status", LoanStatus.OVERDUE));
                if (loan == null) {
                    continue;
                }
                notificationService.create(loan.getStudentId(), NotificationType.OVERDUE,
                        "Loan overdue",
                        "\"" + titleOf(loan.getBookId()).getTitle() + "\" is overdue. Fines accrue at "
                                + money(app.getFinePerDay()) + " per day. Please return it soon.",
                        loan.getId());
                log.info("Loan {} (due {}) is now OVERDUE; fines accrue at {} per day.",
                        loan.getId(), loan.getDueDate(), app.getFinePerDay());
                count++;
            } catch (Exception e) {
                log.error("Overdue job failed for loan {}: {}", candidate.getId(), e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * One-shot reminders, guarded by a conditional flag update per loan so two
     * instances can never send the same reminder twice.
     */
    public int runReminderJobs() {
        int sent = 0;
        Instant now = Instant.now();

        List<Loan> pickupSoon = loanRepository
                .findByStatusAndReminderSentFalseAndPickupDeadlineBetween(
                        LoanStatus.READY_FOR_PICKUP, now, now.plus(6, ChronoUnit.HOURS));
        for (Loan loan : pickupSoon) {
            try {
                UpdateResult result = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(loan.getId())
                                .and("status").is(LoanStatus.READY_FOR_PICKUP)
                                .and("reminderSent").is(false)),
                        new Update().set("reminderSent", true), Loan.class);
                if (result == null || result.getModifiedCount() == 0) {
                    continue;
                }
                loan.setReminderSent(true);
                notificationService.create(loan.getStudentId(), NotificationType.PICKUP_REMINDER,
                        "Pickup deadline approaching",
                        "Pick up \"" + titleOf(loan.getBookId()).getTitle() + "\" before "
                                + DEADLINE_FORMAT.format(loan.getPickupDeadline())
                                + " or the copy will move on.",
                        loan.getId());
                sent++;
            } catch (Exception e) {
                log.error("Pickup reminder failed for loan {}: {}", loan.getId(), e.getMessage(), e);
            }
        }

        List<Loan> dueSoon = loanRepository
                .findByStatusAndDueReminderSentFalseAndDueDateBetween(
                        LoanStatus.ISSUED, now, now.plus(24, ChronoUnit.HOURS));
        for (Loan loan : dueSoon) {
            try {
                UpdateResult result = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(loan.getId())
                                .and("status").is(LoanStatus.ISSUED)
                                .and("dueReminderSent").is(false)),
                        new Update().set("dueReminderSent", true), Loan.class);
                if (result == null || result.getModifiedCount() == 0) {
                    continue;
                }
                loan.setDueReminderSent(true);
                notificationService.create(loan.getStudentId(), NotificationType.DUE_REMINDER,
                        "Due date approaching",
                        "\"" + titleOf(loan.getBookId()).getTitle() + "\" is due on "
                                + DEADLINE_FORMAT.format(loan.getDueDate()) + ".",
                        loan.getId());
                sent++;
            } catch (Exception e) {
                log.error("Due reminder failed for loan {}: {}", loan.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    // ────────────────────────────── DEV helpers ──────────────────────────────

    public LoanResponse devSetDueDate(String loanId, long daysFromNow) {
        Loan loan = requireLoan(loanId);
        loan.setDueDate(Instant.now().plus(daysFromNow, ChronoUnit.DAYS));
        loan = loanRepository.save(loan);
        return toResponse(loan, null, null);
    }

    public LoanResponse devSetPickupDeadline(String loanId, long minutesFromNow) {
        Loan loan = requireLoan(loanId);
        loan.setPickupDeadline(Instant.now().plus(minutesFromNow, ChronoUnit.MINUTES));
        loan = loanRepository.save(loan);
        return toResponse(loan, null, null);
    }

    // ────────────────────────────── QUERIES ──────────────────────────────

    public LoanResponse getLoan(String loanId) {
        Loan loan = requireLoan(loanId);
        return toResponse(loan, null, null);
    }

    public List<LoanResponse> getLoansByStudent(String studentId) {
        return loanRepository.findByStudentIdOrderByRequestDateDesc(studentId).stream()
                .map(loan -> toResponse(loan, null, null))
                .collect(Collectors.toList());
    }

    public List<LoanResponse> getLoansByStatus(LoanStatus status) {
        return loanRepository.findByStatus(status).stream()
                .map(loan -> toResponse(loan, null, null))
                .collect(Collectors.toList());
    }

    /** Page of one student's loans, optionally filtered by a set of statuses. */
    public PageResponse<LoanResponse> getLoansForStudent(String studentId, List<LoanStatus> statuses,
                                                         int page, int size) {
        Criteria criteria = Criteria.where("studentId").is(studentId);
        if (statuses != null && !statuses.isEmpty()) {
            criteria = criteria.and("status").in(statuses);
        }
        return pageLoans(Query.query(criteria), page, size);
    }

    /** Page of all loans, optionally filtered by statuses and a desk search (librarian view). */
    public PageResponse<LoanResponse> getLoansForLibrarian(
            List<LoanStatus> statuses, String search, int page, int size) {
        List<Criteria> clauses = new ArrayList<>();
        if (statuses != null && !statuses.isEmpty()) {
            clauses.add(Criteria.where("status").in(statuses));
        }
        if (search != null && !search.isBlank()) {
            String term = search.trim();
            List<String> studentIds = userRepository
                    .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term).stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            List<String> bookIds = bookRepository.searchByQuery(term).stream()
                    .map(Book::getId)
                    .collect(Collectors.toList());
            List<Criteria> matches = new ArrayList<>();
            if (!studentIds.isEmpty()) {
                matches.add(Criteria.where("studentId").in(studentIds));
            }
            if (!bookIds.isEmpty()) {
                matches.add(Criteria.where("bookId").in(bookIds));
            }
            matches.add(Criteria.where("copyId").regex(Pattern.quote(term), "i"));
            clauses.add(new Criteria().orOperator(matches.toArray(new Criteria[0])));
        }
        Criteria criteria = clauses.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(clauses.toArray(new Criteria[0]));
        return pageLoans(Query.query(criteria), page, size);
    }

    private PageResponse<LoanResponse> pageLoans(Query base, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(100, size));
        long total = mongoTemplate.count(base, Loan.class);
        Query query = base.with(PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "requestDate")));
        List<LoanResponse> items = mongoTemplate.find(query, Loan.class).stream()
                .map(loan -> toResponse(loan, null, null))
                .collect(Collectors.toList());
        return PageResponse.of(items, p, s, total);
    }

    public List<Loan> findByStatusOrderBy(LoanStatus status, String orderByField) {
        return loanRepository.findByStatus(status, Sort.by(Sort.Direction.ASC, orderByField));
    }

    // ────────────────────────────── internals ──────────────────────────────

    /**
     * Conditional status transition; null means the precondition no longer held.
     * Uses updateFirst + modifiedCount so a double click / second instance cannot
     * apply the action twice. The in-memory entity is updated only by the winner.
     */
    private Loan transition(Loan loan, List<LoanStatus> expected, Update update) {
        return transition(loan, expected, update, null);
    }

    private Loan transition(Loan loan, List<LoanStatus> expected, Update update, Criteria extra) {
        Criteria criteria = Criteria.where("_id").is(loan.getId()).and("status").in(expected);
        if (extra != null) {
            criteria = new Criteria().andOperator(criteria, extra);
        }
        Query query = Query.query(criteria);
        UpdateResult result = mongoTemplate.updateFirst(query, update, Loan.class);
        if (result == null || result.getModifiedCount() == 0) {
            return null;
        }
        applySet(loan, update);
        return loan;
    }

    private void applySet(Loan loan, Update update) {
        Object raw = update.getUpdateObject().get("$set");
        if (!(raw instanceof Document set)) {
            return;
        }
        if (set.containsKey("status")) {
            Object value = set.get("status");
            loan.setStatus(value instanceof LoanStatus s ? s : LoanStatus.valueOf(String.valueOf(value)));
        }
        if (set.containsKey("issueDate")) loan.setIssueDate((Instant) set.get("issueDate"));
        if (set.containsKey("dueDate")) loan.setDueDate((Instant) set.get("dueDate"));
        if (set.containsKey("returnDate")) loan.setReturnDate((Instant) set.get("returnDate"));
        if (set.containsKey("copyId")) loan.setCopyId((String) set.get("copyId"));
        if (set.containsKey("pickupDeadline")) loan.setPickupDeadline((Instant) set.get("pickupDeadline"));
        if (set.containsKey("renewalCount")) {
            loan.setRenewalCount(((Number) set.get("renewalCount")).intValue());
        }
        if (set.containsKey("dueReminderSent")) {
            loan.setDueReminderSent(Boolean.TRUE.equals(set.get("dueReminderSent")));
        }
    }

    private void attachFine(Loan loan, Fine fine) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(loan.getId())),
                new Update().set("fineId", fine.getId()), Loan.class);
        loan.setFineId(fine.getId());
    }

    private void requireOwnership(Loan loan, String studentId) {
        if (!loan.getStudentId().equals(studentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only access your own loans.");
        }
    }

    private User requireStudent(String studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + studentId));
        if (user.getRole() != Role.STUDENT) {
            throw new BusinessRuleException("Only students can request loans.");
        }
        return user;
    }

    private Book requireBook(String bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
    }

    private Loan requireLoan(String loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
    }

    /** Atomic points update ($inc) - never read-modify-write. */
    public void awardPoints(String studentId, int points) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(studentId)),
                new Update().inc("points", points),
                User.class);
    }

    public String money(double amount) {
        return "\u20B9" + (int) Math.round(amount);
    }

    public Book titleOf(String bookId) {
        return bookRepository.findById(bookId).orElse(Book.builder()
                .id(bookId).title("Unknown").author("Unknown").subject("Unknown").build());
    }

    public Loan getLoanEntity(String loanId) {
        return requireLoan(loanId);
    }

    /** Enriched DTO: book title, student name, queue position, accrued fine, fine info, pickup deadline. */
    public LoanResponse toResponse(Loan loan, String bookTitle, String studentName) {
        if (bookTitle == null) {
            Book book = bookRepository.findById(loan.getBookId()).orElse(null);
            bookTitle = book != null ? book.getTitle() : "Unknown";
        }
        if (studentName == null) {
            User student = userRepository.findById(loan.getStudentId()).orElse(null);
            studentName = student != null ? student.getName() : "Unknown";
        }

        LoanResponse r = new LoanResponse();
        r.setId(loan.getId());
        r.setStudentId(loan.getStudentId());
        r.setStudentName(studentName);
        r.setBookId(loan.getBookId());
        r.setBookTitle(bookTitle);
        r.setCopyId(loan.getCopyId());
        r.setStatus(loan.getStatus());
        r.setRequestDate(loan.getRequestDate());
        r.setIssueDate(loan.getIssueDate());
        r.setDueDate(loan.getDueDate());
        r.setReturnDate(loan.getReturnDate());
        r.setPickupDeadline(loan.getPickupDeadline());
        r.setRenewalCount(loan.getRenewalCount());
        r.setRenewalLimit(app.getMaxRenewals());
        r.setCanRenew(loan.getStatus() == LoanStatus.ISSUED
                && loan.getDueDate() != null
                && Instant.now().isBefore(loan.getDueDate())
                && loan.getRenewalCount() < app.getMaxRenewals()
                && waitlistService.countWaiting(loan.getBookId()) == 0);

        if (loan.getStatus() == LoanStatus.WAITLISTED) {
            waitlistService.findEntryByLoan(loan.getId()).ifPresent(entry ->
                    r.setQueuePosition(waitlistService.queuePosition(entry)));
        }
        if (loan.getStatus() == LoanStatus.OVERDUE && loan.getDueDate() != null) {
            long secondsLate = Duration.between(loan.getDueDate(), Instant.now()).getSeconds();
            long daysLate = Math.max(0, (long) Math.ceil(secondsLate / 86400.0));
            r.setAccruedFine(daysLate * (double) app.getFinePerDay());
        }
        if (loan.getFineId() != null) {
            fineRepository.findById(loan.getFineId()).ifPresent(fine -> r.setFine(fineService.toDto(fine)));
        }
        return r;
    }
}

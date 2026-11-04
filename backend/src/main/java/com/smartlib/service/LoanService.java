package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.FineDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loan state machine.
 *
 * Transitions (Iteration 3):
 *   READY_FOR_PICKUP -> ISSUED (librarian) | EXPIRED (deadline passed) | CANCELLED (student)
 *   ISSUED -> OVERDUE (job) | REFLECTION_PENDING (on-time return) | FINE_PENDING (late / damaged)
 *   FINE_PENDING -> REFLECTION_PENDING (late or damaged fine paid) | CLOSED (lost fine paid)
 *   REFLECTION_PENDING -> CLOSED (reflection approved / rejected / skipped)
 *
 * RETURNED is no longer a resting state (kept in the enum for legacy data).
 * REFLECTION_PENDING does not count against max-active-loans and does not
 * block new requests for the same book.
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
        // Validate student
        User student = requireStudent(studentId);

        // Validate book
        Book book = requireBook(dto.getBookId());

        // No existing active loan or waitlist entry for the same student+book
        // (REFLECTION_PENDING does not block new requests).
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

        // No pending fines
        if (fineRepository.countByStudentIdAndStatus(studentId, FineStatus.PENDING) > 0) {
            throw new BusinessRuleException("Pay your pending fines first.");
        }

        // Active loan limit
        long active = loanRepository.countByStudentIdAndStatusIn(studentId, ACTIVE_LOAN_STATUSES);
        if (active >= app.getMaxActiveLoans()) {
            throw new BusinessRuleException(
                    "Active loan limit reached (max " + app.getMaxActiveLoans() + ").");
        }

        Instant now = Instant.now();

        // If anybody is already WAITING, this student joins the queue (never skips ahead),
        // even if a copy looks free right now.
        long waiting = waitlistService.countWaiting(book.getId());
        if (waiting > 0) {
            return addToWaitlist(student, book, now, waiting);
        }

        // Try to atomically reserve a copy (AVAILABLE -> RESERVED in one findAndModify,
        // so two students can never both take the last copy).
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

        // No copy could be reserved -> join the queue.
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

        copy.setStatus(CopyStatus.ISSUED);
        copyRepository.save(copy);

        loan.setStatus(LoanStatus.ISSUED);
        loan.setIssueDate(now);
        loan.setDueDate(now.plus(app.getLoanDays(), ChronoUnit.DAYS));
        loan = loanRepository.save(loan);

        notificationService.create(loan.getStudentId(), NotificationType.ISSUED,
                "Book issued",
                "\"" + titleOf(loan.getBookId()).getTitle() + "\" is yours. Due back by "
                        + DEADLINE_FORMAT.format(loan.getDueDate()) + ".",
                loan.getId());

        LoanResponse response = toResponse(loan, null, null);
        response.setMessage("Issued. Due back by " + DEADLINE_FORMAT.format(response.getDueDate()) + ".");
        return response;
    }

    // ────────────────────────────── 5. RETURN ──────────────────────────────

    /**
     * Return handling, including the damaged-book variant: materially borrowed
     * damage turns the copy DAMAGED (kept out of circulation) and creates one
     * fine with the late amount folded in.
     */
    public LoanResponse returnLoan(String loanId, ReturnLoanDto dto) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException(
                    "Only ISSUED or OVERDUE loans can be returned. Current status: " + loan.getStatus());
        }
        boolean damaged = dto != null
                && dto.getBookCondition() == BookCondition.DAMAGED;

        Instant now = Instant.now();
        loan.setReturnDate(now);

        boolean onTime = loan.getDueDate() == null || !now.isAfter(loan.getDueDate());
        long daysLate = onTime ? 0 : (long) Math.max(1, Math.ceil(
                Duration.between(loan.getDueDate(), now).getSeconds() / 86400.0));

        LoanResponse response;
        if (damaged) {
            // The copy is NOT released to the queue; it needs repair first.
            copyRepository.findById(loan.getCopyId()).ifPresent(copy -> {
                copy.setStatus(CopyStatus.DAMAGED);
                copyRepository.save(copy);
            });

            double damagedAmount = app.getDamagedBookFine();
            double lateAmount = daysLate * app.getFinePerDay();
            Fine fine = fineService.createDamaged(loan.getStudentId(), loanId,
                    damagedAmount, lateAmount);
            loan.setFineId(fine.getId());
            loan.setStatus(LoanStatus.FINE_PENDING);
            loan = loanRepository.save(loan);

            notificationService.create(loan.getStudentId(), NotificationType.FINE_IMPOSED,
                    "Fine imposed",
                    "The copy of \"" + titleOf(loan.getBookId()).getTitle()
                            + "\" was returned damaged. Pending fine: " + money(fine.getAmount()) + ".",
                    loan.getId());

            response = toResponse(loan, null, null);
            response.setMessage("Returned DAMAGED. Pending fine: " + money(fine.getAmount())
                    + (daysLate > 0 ? " (damaged-book " + money(damagedAmount) + " + late "
                       + money(lateAmount) + ")" : "")
                    + ". Pay it to move on to your reflection.");
        } else if (onTime) {
            loan.setStatus(LoanStatus.REFLECTION_PENDING);
            loan = loanRepository.save(loan);
            awardPoints(loan.getStudentId(), app.getOnTimeReturnPoints());
            releaseCopyAfterReturn(loan);
            response = toResponse(loan, null, null);
            response.setMessage("Returned on time. +" + app.getOnTimeReturnPoints()
                    + " points! Write a reflection to earn " + app.getReflectionPoints() + " more.");
        } else {
            double amount = daysLate * app.getFinePerDay();
            Fine fine = fineService.create(loan.getStudentId(), loanId, amount, FineReason.LATE_RETURN);
            loan.setFineId(fine.getId());
            loan.setStatus(LoanStatus.FINE_PENDING);
            loan = loanRepository.save(loan);
            releaseCopyAfterReturn(loan);

            notificationService.create(loan.getStudentId(), NotificationType.FINE_IMPOSED,
                    "Fine imposed",
                    "\"" + titleOf(loan.getBookId()).getTitle() + "\" was " + daysLate
                            + " day(s) late. Pending fine: " + money(amount)
                            + ". Pay it to move on to your reflection.",
                    loan.getId());

            response = toResponse(loan, null, null);
            response.setMessage("Late return (" + daysLate + " day(s) overdue). Pending fine: "
                    + money(amount) + ". Pay it to move on to your reflection.");
        }
        return response;
    }

    /** Copy release AFTER the loan row is persisted, so FIFO promotion cannot race with bookkeeping. */
    private void releaseCopyAfterReturn(Loan loan) {
        waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
    }

    // ─────────── 7. LOST ───────────

    public LoanResponse lostLoan(String loanId) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException(
                    "Only ISSUED or OVERDUE loans can be marked lost. Current status: " + loan.getStatus());
        }
        String copyId = loan.getCopyId();
        Copy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found: " + copyId));

        // The copy is NOT released to the queue.
        copy.setStatus(CopyStatus.LOST);
        copyRepository.save(copy);

        loan.setStatus(LoanStatus.LOST);
        loan = loanRepository.save(loan);

        Fine fine = fineService.create(loan.getStudentId(), loanId, app.getLostBookFine(), FineReason.LOST_BOOK);
        loan.setFineId(fine.getId());
        loan.setStatus(LoanStatus.FINE_PENDING);
        loan = loanRepository.save(loan);

        notificationService.create(loan.getStudentId(), NotificationType.FINE_IMPOSED,
                "Fine imposed",
                "\"" + titleOf(loan.getBookId()).getTitle()
                        + "\" was marked lost. Pending fine: " + money(app.getLostBookFine()) + ".",
                loan.getId());

        log.info("Loan {} marked LOST; copy {} is lost (not released to the queue); pending fine {} created.",
                loan.getId(), copy.getId(), fine.getId());
        LoanResponse response = toResponse(loan, null, null);
        response.setMessage("Marked lost. Pending fine: " + money(app.getLostBookFine())
                + ". Pay it to close the loan.");
        return response;
    }

    // ─────────── 8. CANCEL ───────────

    public LoanResponse cancelLoan(String loanId, String studentId) {
        Loan loan = requireLoan(loanId);
        requireOwnership(loan, studentId);
        LoanStatus status = loan.getStatus();

        if (status == LoanStatus.WAITLISTED) {
            waitlistService.cancelByLoanId(loan.getId());
            loan.setStatus(LoanStatus.CANCELLED);
            loan = loanRepository.save(loan);
            LoanResponse response = toResponse(loan, null, null);
            response.setMessage("Request cancelled. You left the queue.");
            return response;
        }

        if (status == LoanStatus.READY_FOR_PICKUP) {
            loan.setStatus(LoanStatus.CANCELLED);
            loan = loanRepository.save(loan);
            waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
            LoanResponse response = toResponse(loan, null, null);
            response.setMessage("Request cancelled. The copy moves to the next student in the queue.");
            return response;
        }

        throw new BusinessRuleException("Loans in status " + status + " cannot be cancelled.");
    }

    // ─────────── SKIP REFLECTION ───────────

    /** Student waives the reflection: loan -> CLOSED, no points. */
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
        loan.setStatus(LoanStatus.CLOSED);
        loan = loanRepository.save(loan);
        LoanResponse response = toResponse(loan, null, null);
        response.setMessage("Reflection skipped. The loan is now CLOSED (no reflection points awarded).");
        return response;
    }

    // ─────────── 3 / 4 / reminders. SCHEDULED JOBS (+dev run-jobs) ───────────

    public int runPickupExpiryJob() {
        Instant now = Instant.now();
        List<Loan> dueToExpire = loanRepository.findByStatusAndPickupDeadlineBefore(
                LoanStatus.READY_FOR_PICKUP, now);
        for (Loan loan : dueToExpire) {
            loan.setStatus(LoanStatus.EXPIRED);
            loan = loanRepository.save(loan);
            waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
            notificationService.create(loan.getStudentId(), NotificationType.EXPIRED,
                    "Pickup window expired",
                    "You did not pick up \"" + titleOf(loan.getBookId()).getTitle()
                            + "\" before the deadline, so the copy moved to the next student.",
                    loan.getId());
            log.info("Pickup deadline passed for loan {} (book {}, copy {}): loan is now EXPIRED, copy released to the queue.",
                    loan.getId(), loan.getBookId(), loan.getCopyId());
        }
        return dueToExpire.size();
    }

    public int runOverdueJob() {
        Instant now = Instant.now();
        List<Loan> dueToOverdue = loanRepository.findByStatusAndDueDateBefore(LoanStatus.ISSUED, now);
        for (Loan loan : dueToOverdue) {
            loan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(loan);
            notificationService.create(loan.getStudentId(), NotificationType.OVERDUE,
                    "Loan overdue",
                    "\"" + titleOf(loan.getBookId()).getTitle() + "\" is overdue. Fines accrue at "
                            + money(app.getFinePerDay()) + " per day. Please return it soon.",
                    loan.getId());
            log.info("Loan {} (due {}) is now OVERDUE; fines accrue at {} per day.",
                    loan.getId(), loan.getDueDate(), app.getFinePerDay());
        }
        return dueToOverdue.size();
    }

    /**
     * One-shot reminders, guarded by flags on the loan so nothing repeats:
     * - pickup deadline approaching (< app reminder window of 6 hours),
     * - due date approaching (24 hours before; ISSUED loans only).
     */
    public int runReminderJobs() {
        int sent = 0;
        Instant now = Instant.now();

        List<Loan> pickupSoon = loanRepository
                .findByStatusAndReminderSentFalseAndPickupDeadlineBetween(
                        LoanStatus.READY_FOR_PICKUP, now, now.plus(6, ChronoUnit.HOURS));
        for (Loan loan : pickupSoon) {
            loan.setReminderSent(true);
            loanRepository.save(loan);
            notificationService.create(loan.getStudentId(), NotificationType.PICKUP_REMINDER,
                    "Pickup deadline approaching",
                    "Pick up \"" + titleOf(loan.getBookId()).getTitle() + "\" before "
                            + DEADLINE_FORMAT.format(loan.getPickupDeadline())
                            + " or the copy will move on.",
                    loan.getId());
            sent++;
        }

        List<Loan> dueSoon = loanRepository
                .findByStatusAndDueReminderSentFalseAndDueDateBetween(
                        LoanStatus.ISSUED, now, now.plus(24, ChronoUnit.HOURS));
        for (Loan loan : dueSoon) {
            loan.setDueReminderSent(true);
            loanRepository.save(loan);
            notificationService.create(loan.getStudentId(), NotificationType.DUE_REMINDER,
                    "Due date approaching",
                    "\"" + titleOf(loan.getBookId()).getTitle() + "\" is due on "
                            + DEADLINE_FORMAT.format(loan.getDueDate()) + ".",
                    loan.getId());
            sent++;
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

    public List<Loan> findByStatusOrderBy(LoanStatus status, String orderByField) {
        return loanRepository.findByStatus(status, Sort.by(Sort.Direction.ASC, orderByField));
    }

    // ────────────────────────────── internals ──────────────────────────────

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

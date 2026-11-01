package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.FineDto;
import com.smartlib.dto.LibrarianActionDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.StudentActionDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.FineRepository;
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
 * ACTIVE_LOAN_STATUSES = {READY_FOR_PICKUP, ISSUED, OVERDUE} (counted against max-active-loans)
 * Duplication guard covers {WAITLISTED, READY_FOR_PICKUP, ISSUED, OVERDUE}.
 * REQUESTED and REFLECTION_PENDING remain unused this iteration.
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
    private final MongoTemplate mongoTemplate;
    private final WaitlistService waitlistService;
    private final FineService fineService;
    private final AppProperties app;

    // ────────────────────────────── 1. REQUEST ──────────────────────────────

    public LoanResponse requestLoan(LoanRequestDto dto) {
        // Validate student
        User student = requireStudent(dto.getStudentId());

        // Validate book
        Book book = requireBook(dto.getBookId());

        // No existing active loan or waitlist entry for the same student+book
        loanRepository.findByStudentIdAndBookIdAndStatusIn(
                        dto.getStudentId(), dto.getBookId(), DUPLICATE_CHECK_STATUSES)
                .ifPresent(l -> {
                    throw new BusinessRuleException(
                            "You already have an active loan for this book (status: " + l.getStatus() + ").");
                });
        if (waitlistRepository.existsByStudentIdAndBookIdAndStatus(
                dto.getStudentId(), dto.getBookId(), WaitlistStatus.WAITING)) {
            throw new BusinessRuleException(
                    "You already have an active loan for this book (status: WAITLISTED).");
        }

        // No pending fines
        if (fineRepository.countByStudentIdAndStatus(dto.getStudentId(), FineStatus.PENDING) > 0) {
            throw new BusinessRuleException("Pay your pending fines first.");
        }

        // Active loan limit
        long active = loanRepository.countByStudentIdAndStatusIn(dto.getStudentId(), ACTIVE_LOAN_STATUSES);
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

    public LoanResponse issueLoan(String loanId, LibrarianActionDto dto) {
        requireLibrarian(dto.getLibrarianId());
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

        LoanResponse response = toResponse(loan, null, null);
        response.setMessage("Issued. Due back by " + DEADLINE_FORMAT.format(response.getDueDate()) + ".");
        return response;
    }

    // ────────────────────────────── 5. RETURN ──────────────────────────────

    public LoanResponse returnLoan(String loanId, LibrarianActionDto dto) {
        requireLibrarian(dto.getLibrarianId());
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException(
                    "Only ISSUED or OVERDUE loans can be returned. Current status: " + loan.getStatus());
        }

        Instant now = Instant.now();
        loan.setReturnDate(now);

        LoanResponse response;
        boolean onTime = loan.getDueDate() == null || !now.isAfter(loan.getDueDate());
        if (onTime) {
            loan.setStatus(LoanStatus.RETURNED);
            loan = loanRepository.save(loan);
            awardOnTimePoints(loan.getStudentId(), app.getOnTimeReturnPoints());
            response = toResponse(loan, null, null);
            response.setMessage("Returned on time. +" + app.getOnTimeReturnPoints() + " points!");
        } else {
            long daysLate = (long) Math.max(1, Math.ceil(
                    Duration.between(loan.getDueDate(), now).getSeconds() / 86400.0));
            double amount = daysLate * app.getFinePerDay();
            Fine fine = fineService.create(loan.getStudentId(), loanId, amount, FineReason.LATE_RETURN);
            loan.setFineId(fine.getId());
            loan.setStatus(LoanStatus.FINE_PENDING);
            loan = loanRepository.save(loan);
            response = toResponse(loan, null, null);
            response.setMessage("Late return (" + daysLate + " day(s) overdue). Pending fine: "
                    + (int) amount + ". Pay it to close the loan.");
        }

        // Release the copy AFTER the loan row is persisted (safe ordering), so
        // FIFO promotion cannot race with fine/points bookkeeping.
        waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
        return response;
    }

    // ────────────────────────────── 7. LOST ──────────────────────────────

    public LoanResponse lostLoan(String loanId, LibrarianActionDto dto) {
        requireLibrarian(dto.getLibrarianId());
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

        log.info("Loan {} marked LOST; copy {} is lost (not released to the queue); pending fine {} created.", loan.getId(), copy.getId(), fine.getId());
        LoanResponse response = toResponse(loan, null, null);
        response.setMessage("Marked lost. Pending fine: " + (int) app.getLostBookFine()
                + ". Pay it to close the loan.");
        return response;
    }

    // ────────────────────────────── 8. CANCEL ──────────────────────────────

    public LoanResponse cancelLoan(String loanId, StudentActionDto dto) {
        Loan loan = requireLoan(loanId);
        if (!loan.getStudentId().equals(dto.getStudentId())) {
            throw new BusinessRuleException("You can only cancel your own loans.");
        }
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

    // ─────────────────── 3 / 4. SCHEDULED JOBS (+dev run-jobs) ───────────────────

    public int runPickupExpiryJob() {
        Instant now = Instant.now();
        List<Loan> dueToExpire = loanRepository.findByStatusAndPickupDeadlineBefore(
                LoanStatus.READY_FOR_PICKUP, now);
        for (Loan loan : dueToExpire) {
            loan.setStatus(LoanStatus.EXPIRED);
            loan = loanRepository.save(loan);
            waitlistService.releaseCopy(loan.getBookId(), loan.getCopyId());
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
            log.info("Loan {} (due {}) is now OVERDUE; fines accrue at {} per day.",
                    loan.getId(), loan.getDueDate(), app.getFinePerDay());
        }
        return dueToOverdue.size();
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

    // ────────────────────────────── internals ──────────────────────────────

    private User requireStudent(String studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + studentId));
        if (user.getRole() != Role.STUDENT) {
            throw new BusinessRuleException("Only students can request loans.");
        }
        return user;
    }

    private User requireLibrarian(String librarianId) {
        User user = userRepository.findById(librarianId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + librarianId));
        if (user.getRole() != Role.LIBRARIAN) {
            throw new BusinessRuleException("Only librarians can perform this action.");
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

    private void awardOnTimePoints(String studentId, int points) {
        userRepository.findById(studentId).ifPresent(student -> {
            student.setPoints(student.getPoints() + points);
            userRepository.save(student);
        });
    }

    public Book titleOf(String bookId) {
        return bookRepository.findById(bookId).orElse(null);
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

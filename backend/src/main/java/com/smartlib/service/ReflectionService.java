package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.ReflectionDto;
import com.smartlib.dto.SubmitReflectionDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reflections complete the loan lifecycle. Review transitions are conditional
 * (PENDING -> APPROVED/REJECTED), so double-clicking Approve awards the points
 * exactly once. One reflection per loan is additionally enforced by a unique
 * index and caught as a 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private final ReflectionRepository reflectionRepository;
    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;
    private final AppProperties app;

    // ─────────── submit (STUDENT, owner) ───────────

    public ReflectionDto submit(String studentId, SubmitReflectionDto dto) {
        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + dto.getLoanId()));
        if (!loan.getStudentId().equals(studentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only submit reflections for your own loans.");
        }
        if (loan.getStatus() != LoanStatus.REFLECTION_PENDING) {
            throw new BusinessRuleException(
                    "Reflections can only be submitted while a loan is REFLECTION_PENDING. Current status: "
                            + loan.getStatus());
        }
        if (reflectionRepository.existsByLoanId(loan.getId())) {
            throw new BusinessRuleException("A reflection for this loan was already submitted and awaits review.");
        }
        String content = dto.getContent().trim();
        if (content.length() < app.getReflectionMinLength()) {
            throw new BusinessRuleException(
                    "Your reflection is too short: at least " + app.getReflectionMinLength()
                            + " characters are required (you wrote " + content.length() + ").");
        }
        if (content.length() > 2000) {
            throw new BusinessRuleException("A reflection must be at most 2000 characters.");
        }

        Reflection reflection;
        try {
            reflection = reflectionRepository.save(Reflection.builder()
                    .studentId(studentId)
                    .loanId(loan.getId())
                    .bookId(loan.getBookId())
                    .content(content)
                    .submissionDate(Instant.now())
                    .status(ReflectionStatus.PENDING)
                    .build());
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("A reflection for this loan was already submitted and awaits review.");
        }

        notificationService.createForLibrarians(NotificationType.REFLECTION_PENDING,
                "New reflection awaits review",
                userRepository.findById(studentId).map(User::getName).orElse("A student")
                        + " submitted a reflection on \""
                        + bookRepository.findById(loan.getBookId()).map(Book::getTitle).orElse("a book")
                        + "\".",
                loan.getId());

        log.info("Reflection {} submitted for loan {} ({}).", reflection.getId(), loan.getId(), studentId);
        return toDto(reflection);
    }

    // ─────────── librarian review ───────────

    public ReflectionDto approve(String reflectionId, String librarianId) {
        Reflection reflection = reflectionRepository.findById(reflectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Reflection not found: " + reflectionId));
        if (reflection.getStatus() != ReflectionStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING reflections can be reviewed. Current status: " + reflection.getStatus());
        }

        Instant now = Instant.now();
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(reflectionId)
                        .and("status").is(ReflectionStatus.PENDING)),
                new Update().set("status", ReflectionStatus.APPROVED)
                        .set("reviewedBy", librarianId)
                        .set("reviewedDate", now),
                Reflection.class);
        if (result == null || result.getModifiedCount() == 0) {
            throw new BusinessRuleException("This reflection was already reviewed. Refresh and try again.");
        }
        reflection.setStatus(ReflectionStatus.APPROVED);
        reflection.setReviewedBy(librarianId);
        reflection.setReviewedDate(now);

        closeLoan(reflection.getLoanId());

        // Only the single winning review reaches this point, so points are never duplicated.
        int points = app.getReflectionPoints();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(reflection.getStudentId())),
                new Update().inc("points", points),
                User.class);

        String bookTitle = bookRepository.findById(reflection.getBookId())
                .map(Book::getTitle).orElse("your book");
        notificationService.create(reflection.getStudentId(), NotificationType.REFLECTION_APPROVED,
                "Reflection approved",
                "Your reflection on \"" + bookTitle + "\" was approved: +" + points + " points!",
                reflection.getLoanId());
        log.info("Reflection {} approved by {}; +{} points for {}.",
                reflection.getId(), librarianId, points, reflection.getStudentId());
        return toDto(reflection);
    }

    public ReflectionDto reject(String reflectionId, String librarianId, String reviewNote) {
        Reflection reflection = reflectionRepository.findById(reflectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Reflection not found: " + reflectionId));
        if (reflection.getStatus() != ReflectionStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING reflections can be reviewed. Current status: " + reflection.getStatus());
        }
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BusinessRuleException("A review note is required when rejecting a reflection.");
        }
        if (reviewNote.trim().length() > 500) {
            throw new BusinessRuleException("The review note must be at most 500 characters.");
        }

        Instant now = Instant.now();
        String note = reviewNote.trim();
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(reflectionId)
                        .and("status").is(ReflectionStatus.PENDING)),
                new Update().set("status", ReflectionStatus.REJECTED)
                        .set("reviewedBy", librarianId)
                        .set("reviewedDate", now)
                        .set("reviewNote", note),
                Reflection.class);
        if (result == null || result.getModifiedCount() == 0) {
            throw new BusinessRuleException("This reflection was already reviewed. Refresh and try again.");
        }
        reflection.setStatus(ReflectionStatus.REJECTED);
        reflection.setReviewedBy(librarianId);
        reflection.setReviewedDate(now);
        reflection.setReviewNote(note);

        closeLoan(reflection.getLoanId());

        String bookTitle = bookRepository.findById(reflection.getBookId())
                .map(Book::getTitle).orElse("your book");
        notificationService.create(reflection.getStudentId(), NotificationType.REFLECTION_REJECTED,
                "Reflection rejected",
                "Your reflection on \"" + bookTitle + "\" was not approved. Librarian's note: "
                        + reflection.getReviewNote(),
                reflection.getLoanId());
        log.info("Reflection {} rejected by {} (no points awarded).", reflection.getId(), librarianId);
        return toDto(reflection);
    }

    private void closeLoan(String loanId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(loanId)
                        .and("status").is(LoanStatus.REFLECTION_PENDING)),
                new Update().set("status", LoanStatus.CLOSED), Loan.class);
    }

    // ─────────── queries ───────────

    public List<ReflectionDto> mine(String studentId) {
        return reflectionRepository.findByStudentIdOrderBySubmissionDateDesc(studentId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<ReflectionDto> byStatus(ReflectionStatus status) {
        return reflectionRepository.findByStatusOrderBySubmissionDateAsc(status).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ReflectionDto forLoan(String loanId) {
        return reflectionRepository.findByLoanId(loanId).map(this::toDto).orElse(null);
    }

    // ─────────── mapping ───────────

    public ReflectionDto toDto(Reflection r) {
        ReflectionDto d = new ReflectionDto();
        d.setId(r.getId());
        d.setStudentId(r.getStudentId());
        d.setLoanId(r.getLoanId());
        d.setBookId(r.getBookId());
        d.setContent(r.getContent());
        d.setSubmissionDate(r.getSubmissionDate());
        d.setStatus(r.getStatus());
        d.setReviewedBy(r.getReviewedBy());
        d.setReviewedDate(r.getReviewedDate());
        d.setReviewNote(r.getReviewNote());
        d.setStudentName(userRepository.findById(r.getStudentId()).map(User::getName).orElse("Unknown"));
        d.setBookTitle(bookRepository.findById(r.getBookId()).map(Book::getTitle).orElse("Unknown"));
        return d;
    }
}

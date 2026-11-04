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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reflections complete the loan lifecycle:
 * after a (paid fines or on-time) return the loan is REFLECTION_PENDING until
 * the student submits one; approve/reject both close the loan, approval awards
 * points exactly once.
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

        Reflection reflection = reflectionRepository.save(Reflection.builder()
                .studentId(studentId)
                .loanId(loan.getId())
                .bookId(loan.getBookId())
                .content(content)
                .submissionDate(Instant.now())
                .status(ReflectionStatus.PENDING)
                .build());

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
        Reflection reflection = requirePending(reflectionId);
        reflection.setStatus(ReflectionStatus.APPROVED);
        reflection.setReviewedBy(librarianId);
        reflection.setReviewedDate(Instant.now());
        reflection = reflectionRepository.save(reflection);

        closeLoan(reflection.getLoanId());

        // Points are awarded atomically and exactly once (status gate above is the only entry point).
        int points = app.getReflectionPoints();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(reflection.getStudentId())),
                new Update().inc("points", points),
                User.class);

        String bookTitle = bookRepository.findById(reflection.getBookId()).map(Book::getTitle).orElse("your book");
        notificationService.create(reflection.getStudentId(), NotificationType.REFLECTION_APPROVED,
                "Reflection approved",
                "Your reflection on \"" + bookTitle + "\" was approved: +" + points + " points!",
                reflection.getLoanId());
        log.info("Reflection {} approved by {}; +{} points for {}.",
                reflection.getId(), librarianId, points, reflection.getStudentId());
        return toDto(reflection);
    }

    public ReflectionDto reject(String reflectionId, String librarianId, String reviewNote) {
        Reflection reflection = requirePending(reflectionId);
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BusinessRuleException("A review note is required when rejecting a reflection.");
        }
        reflection.setStatus(ReflectionStatus.REJECTED);
        reflection.setReviewedBy(librarianId);
        reflection.setReviewedDate(Instant.now());
        reflection.setReviewNote(reviewNote.trim());
        reflection = reflectionRepository.save(reflection);

        closeLoan(reflection.getLoanId());

        String bookTitle = bookRepository.findById(reflection.getBookId()).map(Book::getTitle).orElse("your book");
        notificationService.create(reflection.getStudentId(), NotificationType.REFLECTION_REJECTED,
                "Reflection rejected",
                "Your reflection on \"" + bookTitle + "\" was not approved. Librarian's note: "
                        + reflection.getReviewNote(),
                reflection.getLoanId());
        log.info("Reflection {} rejected by {} (no points awarded).", reflection.getId(), librarianId);
        return toDto(reflection);
    }

    private Reflection requirePending(String reflectionId) {
        Reflection reflection = reflectionRepository.findById(reflectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Reflection not found: " + reflectionId));
        if (reflection.getStatus() != ReflectionStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING reflections can be reviewed. Current status: " + reflection.getStatus());
        }
        return reflection;
    }

    private void closeLoan(String loanId) {
        loanRepository.findById(loanId).ifPresent(loan -> {
            if (loan.getStatus() == LoanStatus.REFLECTION_PENDING) {
                loan.setStatus(LoanStatus.CLOSED);
                loanRepository.save(loan);
            }
        });
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

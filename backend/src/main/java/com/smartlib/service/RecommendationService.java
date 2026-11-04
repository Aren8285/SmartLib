package com.smartlib.service;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.RecommendationDto;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based recommendations (no ML). Signals for a student:
 *  - subject affinity: past loans in the subject (+ extra per approved reflection,
 *    plus a base weight for registration preferred subjects),
 *  - similar-reader: other students who borrowed the student's books, and what
 *    "my readers" borrowed that this student hasn't,
 *  - availability nudge for actionable books,
 *  - cold start: preferred subjects first, then popularity across the library.
 * Books the student currently has an active loan or waitlist entry for are
 * excluded; unread books are preferred over already-read ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    public static final List<LoanStatus> READ_HISTORY_STATUSES = List.of(
            LoanStatus.RETURNED, LoanStatus.REFLECTION_PENDING, LoanStatus.CLOSED);
    public static final List<LoanStatus> ACTIVE_EXCLUSION_STATUSES = List.of(
            LoanStatus.WAITLISTED, LoanStatus.READY_FOR_PICKUP, LoanStatus.ISSUED, LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final WaitlistRepository waitlistRepository;
    private final ReflectionRepository reflectionRepository;
    private final UserRepository userRepository;
    private final BookService bookService;
    private final AppProperties app;

    public List<RecommendationDto> recommendFor(String studentId) {
        Objects.requireNonNull(studentId, "studentId");

        Map<String, Double> subjectWeight = new HashMap<>();
        Set<String> readBookIds = new HashSet<>();
        Set<String> activeBookIds = new HashSet<>();
        Map<String, Integer> similarBorrowWeight = new HashMap<>();
        Map<String, String> similarBorrowWitness = new HashMap<>();

        // Subject affinity from reading history; approved reflections add extra weight.
        for (Loan loan : loanRepository.findByStudentIdAndStatusIn(studentId, READ_HISTORY_STATUSES)) {
            readBookIds.add(loan.getBookId());
            double approved = reflectionRepository.findByLoanId(loan.getId())
                    .map(r -> r.getStatus() == ReflectionStatus.APPROVED ? 1.0 : 0.0)
                    .orElse(0.0);
            bookRepository.findById(loan.getBookId()).ifPresent(book ->
                    subjectWeight.merge(book.getSubject(), 1.0 + approved, Double::sum));
        }
        // Active loans and open queue entries are excluded.
        for (Loan loan : loanRepository.findByStudentIdAndStatusIn(studentId, ACTIVE_EXCLUSION_STATUSES)) {
            activeBookIds.add(loan.getBookId());
        }
        for (WaitlistEntry entry : waitlistRepository.findByStudentIdAndStatus(studentId, WaitlistStatus.WAITING)) {
            activeBookIds.add(entry.getBookId());
        }
        // Registration preferences: flat base boost per preferred subject.
        User student = userRepository.findById(studentId).orElse(null);
        if (student != null && student.getPreferredSubjects() != null) {
            student.getPreferredSubjects().forEach(subject ->
                    subjectWeight.merge(subject, 0.5, Double::sum));
        }

        // Similar readers: everyone else who read a book this student read.
        // The "witness" is the shared book, so the reason text makes sense.
        Map<String, Set<String>> sharedPeers = new HashMap<>();
        for (String readBookId : readBookIds) {
            for (Loan sameBookLoan : loanRepository.findByBookIdAndStatusIn(readBookId, READ_HISTORY_STATUSES)) {
                if (!sameBookLoan.getStudentId().equals(studentId)) {
                    sharedPeers.computeIfAbsent(readBookId, k -> new HashSet<>()).add(sameBookLoan.getStudentId());
                }
            }
        }
        Map<String, List<Loan>> peerHistoryCache = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : sharedPeers.entrySet()) {
            String sharedBookTitle = bookRepository.findById(e.getKey()).map(Book::getTitle).orElse("a book you read");
            for (String peer : e.getValue()) {
                List<Loan> peerHistory = peerHistoryCache.computeIfAbsent(peer,
                        p -> loanRepository.findByStudentIdAndStatusIn(p, READ_HISTORY_STATUSES));
                for (Loan peerLoan : peerHistory) {
                    String peerBookId = peerLoan.getBookId();
                    if (readBookIds.contains(peerBookId) || activeBookIds.contains(peerBookId)) {
                        continue; // already-read books and active books don't get peer credit here
                    }
                    similarBorrowWeight.merge(peerBookId, 1, Integer::sum);
                    similarBorrowWitness.putIfAbsent(peerBookId, sharedBookTitle);
                }
            }
        }

        // ── scoring ──
        List<Scored> scored = new ArrayList<>();
        for (Book book : bookRepository.findAll()) {
            if (activeBookIds.contains(book.getId())) {
                continue;
            }
            double subjectScore = subjectWeight.getOrDefault(book.getSubject(), 0.0);
            double peerScore = similarBorrowWeight.getOrDefault(book.getId(), 0) * 0.1;
            if (subjectScore <= 0 && peerScore <= 0) {
                continue; // no signal: the availability nudge alone is not a recommendation
            }
            double availability = copyRepository.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE) > 0
                    ? 0.3 : 0.0;
            double total = subjectScore + peerScore + availability;
            String reason;
            boolean hasHistory = !readBookIds.isEmpty();
            if (peerScore >= subjectScore && peerScore > 0) {
                reason = "Readers who borrowed '" + similarBorrowWitness.getOrDefault(book.getId(), "a book you read")
                        + "' also borrowed this";
            } else {
                reason = (hasHistory ? "Because you enjoy " : "Because you prefer ") + book.getSubject();
            }
            scored.add(new Scored(book, reason, total, readBookIds.contains(book.getId())));
        }

        // Prefer unread books; already-read books qualify only when nothing else does.
        List<Scored> unread = scored.stream().filter(s -> !s.read).collect(Collectors.toList());
        if (unread.isEmpty() && scored.isEmpty()) {
            return coldStart(studentId, activeBookIds);
        }

        List<Scored> pool = unread.isEmpty() ? scored : unread;
        return pool.stream()
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(app.getRecommendationLimit())
                .map(s -> toDto(s.book(), s.reason(), s.score()))
                .collect(Collectors.toList());
    }

    /** No history at all: preferred subjects first, then overall popularity. */
    private List<RecommendationDto> coldStart(String studentId, Set<String> exclude) {
        List<String> preferredSubjects = userRepository.findById(studentId)
                .map(User::getPreferredSubjects)
                .filter(Objects::nonNull)
                .orElse(List.of());
        List<RecommendationDto> result = new ArrayList<>();

        for (Book book : bookRepository.findAll().stream()
                .filter(book -> !exclude.contains(book.getId())
                        && preferredSubjects.contains(book.getSubject()))
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList())) {
            result.add(toDto(book, "Because you prefer " + book.getSubject(), 1.0));
            if (result.size() >= app.getRecommendationLimit()) {
                return result;
            }
        }
        for (Book book : bookRepository.findAll().stream()
                .filter(book -> !exclude.contains(book.getId())
                        && result.stream().noneMatch(r -> r.getBook().getId().equals(book.getId())))
                .sorted(Comparator.comparingLong((Book b) -> -loanRepository.countByBookId(b.getId())))
                .collect(Collectors.toList())) {
            result.add(toDto(book, "Popular in the library", 0.5));
            if (result.size() >= app.getRecommendationLimit()) {
                return result;
            }
        }
        return result;
    }

    private RecommendationDto toDto(Book book, String reason, double score) {
        RecommendationDto dto = new RecommendationDto();
        dto.setBook(bookService.toResponse(book));
        dto.setReason(reason);
        dto.setScore(Math.round(score * 100.0) / 100.0);
        return dto;
    }

    private record Scored(Book book, String reason, double score, boolean read) {
    }
}

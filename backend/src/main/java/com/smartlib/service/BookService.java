package com.smartlib.service;

import com.smartlib.dto.AddCopiesDto;
import com.smartlib.dto.BookRequestDto;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.CopyDto;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    /** Non-terminal loan statuses that must block a book deletion. */
    static final List<LoanStatus> NON_TERMINAL_LOAN_STATUSES = List.of(
            LoanStatus.WAITLISTED, LoanStatus.READY_FOR_PICKUP, LoanStatus.ISSUED,
            LoanStatus.OVERDUE, LoanStatus.FINE_PENDING, LoanStatus.REFLECTION_PENDING);

    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final WaitlistRepository waitlistRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;

    /**
     * Combined filters: case-insensitive text on title/author (also subject, kept from
     * iteration 1), optional exact subject, optional availableOnly.
     */
    public List<BookResponse> getBooks(String query, String subject, boolean availableOnly) {
        List<Book> books;
        if (StringUtils.hasText(query)) {
            books = bookRepository.searchByQuery(query);
        } else {
            books = bookRepository.findAll();
        }
        return books.stream()
                .filter(b -> !StringUtils.hasText(subject) || b.getSubject().equals(subject))
                .filter(b -> !availableOnly
                        || copyRepository.countByBookIdAndStatus(b.getId(), CopyStatus.AVAILABLE) > 0)
                .map(this::toResponse)
                .sorted(Comparator.comparing(BookResponse::getTitle))
                .collect(Collectors.toList());
    }

    public List<String> getSubjects() {
        return bookRepository.findAll().stream()
                .map(Book::getSubject)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public BookResponse getBook(String id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        return toResponse(book);
    }

    // ─────────── Iteration 3: management (LIBRARIAN) ───────────

    public BookResponse create(BookRequestDto dto) {
        String isbn = validateIsbn(dto.getIsbn(), null);
        Book book = bookRepository.save(Book.builder()
                .title(dto.getTitle().trim())
                .author(dto.getAuthor().trim())
                .subject(dto.getSubject().trim())
                .isbn(isbn)
                .build());
        log.info("Book created: {} ({})", book.getTitle(), book.getSubject());
        return toResponse(book);
    }

    public BookResponse update(String id, BookRequestDto dto) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        String isbn = validateIsbn(dto.getIsbn(), id);
        book.setTitle(dto.getTitle().trim());
        book.setAuthor(dto.getAuthor().trim());
        book.setSubject(dto.getSubject().trim());
        book.setIsbn(isbn);
        book = bookRepository.save(book);
        log.info("Book updated: {} ({})", book.getTitle(), book.getId());
        return toResponse(book);
    }

    /**
     * Delete only when nothing depends on the book: no RESERVED/ISSUED copies,
     * no non-terminal loans and no WAITING waitlist entries. Deletes the book
     * and its remaining copies.
     */
    public void delete(String id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));

        long heldCopies = copyRepository.countByBookIdAndStatus(id, CopyStatus.RESERVED)
                + copyRepository.countByBookIdAndStatus(id, CopyStatus.ISSUED);
        if (heldCopies > 0) {
            throw new BusinessRuleException(
                    "This book cannot be deleted while copies are reserved or issued.");
        }
        if (loanRepository.existsByBookIdAndStatusIn(id, NON_TERMINAL_LOAN_STATUSES)) {
            throw new BusinessRuleException(
                    "This book cannot be deleted while loans or waitlist requests are still in progress.");
        }
        if (waitlistRepository.countByBookIdAndStatus(id, WaitlistStatus.WAITING) > 0) {
            throw new BusinessRuleException(
                    "This book cannot be deleted while students are still waiting for it.");
        }

        List<Copy> copies = copyRepository.findByBookId(id);
        copyRepository.deleteAll(copies);
        bookRepository.delete(book);
        log.info("Book deleted: {} ({} copy/copies removed)", book.getTitle(), copies.size());
    }

    // ─────────── copies ───────────

    public List<CopyDto> getCopies(String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        return copyRepository.findByBookId(book.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Adds copies as AVAILABLE, then runs the shared release routine for each so
     * waiting students are promoted immediately (notifications included).
     */
    public List<CopyDto> addCopies(String bookId, AddCopiesDto dto) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        String condition = StringUtils.hasText(dto.getCondition()) ? dto.getCondition().trim() : "GOOD";

        List<Copy> added = new ArrayList<>();
        for (int i = 0; i < dto.getCount(); i++) {
            Copy copy = copyRepository.save(Copy.builder()
                    .bookId(bookId)
                    .status(CopyStatus.AVAILABLE)
                    .condition(condition)
                    .build());
            added.add(copy);
            // Promote the FIFO queue as soon as the copy exists; if nobody waits the copy stays AVAILABLE.
            waitlistService.releaseCopy(bookId, copy.getId());
            log.info("Copy {} added to book {} ({}).", copy.getId(), bookId, condition);
        }
        return added.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Manual transitions only:
     *   AVAILABLE -> DAMAGED | AVAILABLE -> LOST | DAMAGED -> AVAILABLE (repaired).
     * Copies that are RESERVED or ISSUED belong to a student and cannot change here.
     */
    public CopyDto updateCopyStatus(String copyId, UpdateCopyStatusDto dto) {
        Copy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found: " + copyId));
        if (copy.getStatus() == CopyStatus.RESERVED || copy.getStatus() == CopyStatus.ISSUED) {
            throw new BusinessRuleException(
                    "This copy is held by a student (" + copy.getStatus() + "); process a return or cancel first.");
        }
        CopyStatus from = copy.getStatus();
        CopyStatus to = dto.getStatus();
        boolean allowed = (from == CopyStatus.AVAILABLE && to == CopyStatus.DAMAGED)
                || (from == CopyStatus.AVAILABLE && to == CopyStatus.LOST)
                || (from == CopyStatus.DAMAGED && to == CopyStatus.AVAILABLE);
        if (!allowed) {
            throw new BusinessRuleException(
                    "Unsupported copy status change " + from + " -> " + to
                            + ". Allowed: AVAILABLE ↔ DAMAGED (repair) and AVAILABLE → LOST.");
        }
        if (StringUtils.hasText(dto.getCondition())) {
            copy.setCondition(dto.getCondition().trim());
        }
        copy.setStatus(to);
        copy = copyRepository.save(copy);

        if (from == CopyStatus.DAMAGED && to == CopyStatus.AVAILABLE) {
            // Repaired: release so a waiting student is promoted immediately.
            waitlistService.releaseCopy(copy.getBookId(), copy.getId());
            log.info("Damaged copy {} repaired and released into circulation.", copy.getId());
        }
        return toDto(copy);
    }

    public Map<String, Long> copyStatusCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (CopyStatus status : CopyStatus.values()) {
            long count = copyRepository.countByStatus(status);
            if (count > 0) counts.put(status.name(), count);
        }
        return counts;
    }

    public long countCopies() {
        return copyRepository.count();
    }

    public long countBooks() {
        return bookRepository.count();
    }

    // ─────────── mapping & validation ───────────

    public CopyDto toDto(Copy copy) {
        CopyDto d = new CopyDto();
        d.setId(copy.getId());
        d.setBookId(copy.getBookId());
        d.setStatus(copy.getStatus());
        d.setCondition(copy.getCondition());
        if (copy.getStatus() == CopyStatus.RESERVED || copy.getStatus() == CopyStatus.ISSUED) {
            loanRepository.findByCopyIdAndStatusIn(copy.getId(), List.of(
                            LoanStatus.READY_FOR_PICKUP, LoanStatus.ISSUED, LoanStatus.OVERDUE)).stream()
                    .findFirst()
                    .ifPresent(loan -> {
                        d.setHolderStudentId(loan.getStudentId());
                        d.setHolderName(userRepository.findById(loan.getStudentId())
                                .map(User::getName).orElse("Unknown"));
                    });
        }
        return d;
    }

    private String validateIsbn(String isbn, String currentBookId) {
        if (!StringUtils.hasText(isbn)) {
            return null;
        }
        String clean = isbn.replaceAll("[\\s-]", "").toUpperCase();
        boolean validIsbn10 = clean.matches("^\\d{9}[\\dX]$") && validIsbn10Checksum(clean);
        boolean validIsbn13 = clean.matches("^\\d{13}$") && validIsbn13Checksum(clean);
        if (!validIsbn10 && !validIsbn13) {
            throw new BusinessRuleException(
                    "The ISBN is not a well-formed ISBN-10 or ISBN-13.");
        }
        Book existing = bookRepository.findByIsbn(clean);
        if (existing != null && !existing.getId().equals(currentBookId)) {
            throw new BusinessRuleException(
                    "The ISBN is already in use by \"" + existing.getTitle() + "\".");
        }
        return clean;
    }

    private boolean validIsbn10Checksum(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int value = (c == 'X') ? 10 : c - '0';
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }

    private boolean validIsbn13Checksum(String isbn) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int value = isbn.charAt(i) - '0';
            sum += (i % 2 == 0) ? value : value * 3;
        }
        return sum % 10 == 0;
    }

    // ─────────── existing mapping ───────────

    public BookResponse toResponse(Book book) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setTitle(book.getTitle());
        r.setAuthor(book.getAuthor());
        r.setSubject(book.getSubject());
        r.setIsbn(book.getIsbn());
        r.setAvailableCopies(copyRepository.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE));
        r.setTotalCopies(copyRepository.countByBookId(book.getId()));
        r.setWaitlistSize(waitlistRepository.countByBookIdAndStatus(book.getId(), WaitlistStatus.WAITING));
        return r;
    }
}

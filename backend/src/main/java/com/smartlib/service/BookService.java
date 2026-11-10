package com.smartlib.service;

import com.smartlib.dto.AddCopiesDto;
import com.smartlib.dto.BookRequestDto;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.CopyDto;
import com.smartlib.dto.PageResponse;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ShelfRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
    private final ShelfRepository shelfRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Server-side filtered, sorted and paginated catalogue. The response is a
     * page envelope so the frontend never has to load the whole library.
     */
    public PageResponse<BookResponse> getBooksPage(String query, String subject, Boolean availableOnly,
                                                   String shelfCode,
                                                   int page, int size, String sort) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(100, size));

        List<Criteria> filters = new ArrayList<>();
        if (StringUtils.hasText(query)) {
            String escaped = Pattern.quote(query.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(escaped, "i"),
                    Criteria.where("author").regex(escaped, "i"),
                    Criteria.where("subject").regex(escaped, "i"),
                    Criteria.where("isbn").regex(escaped, "i")));
        }
        if (StringUtils.hasText(subject)) {
            filters.add(Criteria.where("subject").is(subject));
        }
        if (StringUtils.hasText(shelfCode)) {
            Shelf shelf = shelfRepository.findByCode(shelfCode.trim()).orElse(null);
            if (shelf == null) {
                return PageResponse.of(List.of(), p, s, 0);
            }
            filters.add(Criteria.where("shelfId").is(shelf.getId()));
        }
        if (Boolean.TRUE.equals(availableOnly)) {
            List<String> availableBookIds = mongoTemplate.findDistinct(
                    Query.query(Criteria.where("status").is(CopyStatus.AVAILABLE)),
                    "bookId", Copy.class, String.class);
            if (availableBookIds.isEmpty()) {
                return PageResponse.of(List.of(), p, s, 0);
            }
            filters.add(Criteria.where("_id").in(availableBookIds));
        }

        Criteria criteria = filters.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(filters.toArray(new Criteria[0]));
        Query base = Query.query(criteria);
        long total = mongoTemplate.count(base, Book.class);
        Query pageQuery = base.with(PageRequest.of(p, s, sortFor(sort)));
        List<BookResponse> items = mongoTemplate.find(pageQuery, Book.class).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(items, p, s, total);
    }

    private Sort sortFor(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.ASC, "title");
        }
        return switch (sort.trim().toLowerCase()) {
            case "author" -> Sort.by(Sort.Direction.ASC, "author");
            case "author_desc" -> Sort.by(Sort.Direction.DESC, "author");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "title_desc" -> Sort.by(Sort.Direction.DESC, "title");
            default -> Sort.by(Sort.Direction.ASC, "title");
        };
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

    // ─────────── Iteration 3/4: management (LIBRARIAN) ───────────

    public BookResponse create(BookRequestDto dto) {
        String isbn = validateIsbn(dto.getIsbn(), null);
        String shelfId = resolveShelf(dto.getShelfId(), null);
        Book book = bookRepository.save(Book.builder()
                .title(dto.getTitle().trim())
                .author(dto.getAuthor().trim())
                .subject(dto.getSubject().trim())
                .isbn(isbn)
                .shelfId(shelfId)
                .createdAt(Instant.now())
                .build());
        log.info("Book created: {} ({})", book.getTitle(), book.getSubject());
        return toResponse(book);
    }

    public BookResponse update(String id, BookRequestDto dto) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        String isbn = validateIsbn(dto.getIsbn(), id);
        String shelfId = resolveShelf(dto.getShelfId(), id);
        boolean shelfChanged = !java.util.Objects.equals(book.getShelfId(), shelfId);
        book.setTitle(dto.getTitle().trim());
        book.setAuthor(dto.getAuthor().trim());
        book.setSubject(dto.getSubject().trim());
        book.setIsbn(isbn);
        book.setShelfId(shelfId);
        book = bookRepository.save(book);
        if (shelfChanged) {
            applyShelfToCopies(book.getId(), shelfId);
        }
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
                    .shelfId(book.getShelfId())
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
                            + ". Allowed: AVAILABLE <-> DAMAGED (repair) and AVAILABLE -> LOST.");
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

    /** Used by reports; avoids loading statuses one by one. */
    public long countCopiesByStatus(CopyStatus status) {
        return copyRepository.countByStatus(status);
    }

    // ─────────── mapping & validation ───────────

    public CopyDto toDto(Copy copy) {
        CopyDto d = new CopyDto();
        d.setId(copy.getId());
        d.setBookId(copy.getBookId());
        d.setStatus(copy.getStatus());
        d.setCondition(copy.getCondition());
        d.setShelfId(copy.getShelfId());
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

    /** Resolves an optional shelf id, validating existence and free capacity. */
    private String resolveShelf(String shelfId, String currentBookId) {
        if (!StringUtils.hasText(shelfId)) {
            return null;
        }
        Shelf shelf = shelfRepository.findById(shelfId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Shelf not found: " + shelfId));
        long onShelf = bookRepository.countByShelfId(shelf.getId());
        if (currentBookId != null) {
            Book current = bookRepository.findById(currentBookId).orElse(null);
            if (current != null && shelf.getId().equals(current.getShelfId())) {
                return shelf.getId(); // unchanged, no capacity re-check
            }
        }
        if (onShelf >= shelf.getCapacity()) {
            throw new BusinessRuleException(
                    "Shelf " + shelf.getCode() + " is full (" + onShelf + "/" + shelf.getCapacity() + ").");
        }
        return shelf.getId();
    }

    private void applyShelfToCopies(String bookId, String shelfId) {
        List<Copy> copies = copyRepository.findByBookId(bookId);
        copies.forEach(copy -> copy.setShelfId(shelfId));
        if (!copies.isEmpty()) {
            copyRepository.saveAll(copies);
        }
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
        r.setShelfId(book.getShelfId());
        if (book.getShelfId() != null) {
            shelfRepository.findById(book.getShelfId()).ifPresent(shelf -> {
                r.setShelfSection(shelf.getSection());
                r.setShelfCode(shelf.getCode());
            });
        }
        r.setAvailableCopies(copyRepository.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE));
        r.setTotalCopies(copyRepository.countByBookId(book.getId()));
        r.setWaitlistSize(waitlistRepository.countByBookIdAndStatus(book.getId(), WaitlistStatus.WAITING));
        return r;
    }
}

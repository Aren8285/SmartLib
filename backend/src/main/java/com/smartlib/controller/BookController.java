package com.smartlib.controller;

import com.smartlib.dto.AddCopiesDto;
import com.smartlib.dto.AssignShelfDto;
import com.smartlib.dto.BookRequestDto;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.CopyDto;
import com.smartlib.dto.PageResponse;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.service.BookService;
import com.smartlib.service.ShelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final ShelfService shelfService;

    /** Paginated catalogue: filters + sort + page envelope. */
    @GetMapping
    public PageResponse<BookResponse> getBooks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false, name = "availableOnly") Boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "title") String sort) {
        return bookService.getBooksPage(query, subject, availableOnly, page, size, sort);
    }

    @GetMapping("/subjects")
    public List<String> getSubjects() {
        return bookService.getSubjects();
    }

    @GetMapping("/{id}")
    public BookResponse getBook(@PathVariable String id) {
        return bookService.getBook(id);
    }

    // ─────────── management (LIBRARIAN) ───────────

    @PostMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody BookRequestDto dto) {
        return bookService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookResponse update(@PathVariable String id, @Valid @RequestBody BookRequestDto dto) {
        return bookService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public Map<String, Object> delete(@PathVariable String id) {
        bookService.delete(id);
        return Map.of("deleted", true);
    }

    /** Assign the book (and its copies) to a shelf; blank shelfId clears it. */
    @PatchMapping("/{id}/shelf")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookResponse assignShelf(@PathVariable String id, @Valid @RequestBody AssignShelfDto dto) {
        return shelfService.assignBook(id, dto.getShelfId());
    }

    @GetMapping("/{id}/copies")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public List<CopyDto> getCopies(@PathVariable String id) {
        return bookService.getCopies(id);
    }

    @PostMapping("/{id}/copies")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public List<CopyDto> addCopies(@PathVariable String id, @Valid @RequestBody AddCopiesDto dto) {
        return bookService.addCopies(id, dto);
    }
}

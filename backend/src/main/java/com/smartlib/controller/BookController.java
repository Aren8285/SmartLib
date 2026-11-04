package com.smartlib.controller;

import com.smartlib.dto.AddCopiesDto;
import com.smartlib.dto.BookRequestDto;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.CopyDto;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.service.BookService;
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

    @GetMapping
    public List<BookResponse> getBooks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false, name = "availableOnly") Boolean availableOnly) {
        return bookService.getBooks(query, subject, Boolean.TRUE.equals(availableOnly));
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

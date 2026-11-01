package com.smartlib.controller;

import com.smartlib.dto.BookResponse;
import com.smartlib.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}

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
    public List<BookResponse> getBooks(@RequestParam(required = false) String query) {
        return bookService.getBooks(query);
    }

    @GetMapping("/{id}")
    public BookResponse getBook(@PathVariable String id) {
        return bookService.getBook(id);
    }
}


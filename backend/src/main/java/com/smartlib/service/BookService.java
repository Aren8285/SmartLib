package com.smartlib.service;

import com.smartlib.dto.BookResponse;
import com.smartlib.model.Book;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.CopyStatus;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;

    public List<BookResponse> getBooks(String query) {
        List<Book> books;
        if (StringUtils.hasText(query)) {
            books = bookRepository.searchByQuery(query);
        } else {
            books = bookRepository.findAll();
        }
        return books.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BookResponse getBook(String id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        return toResponse(book);
    }

    public BookResponse toResponse(Book book) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setTitle(book.getTitle());
        r.setAuthor(book.getAuthor());
        r.setSubject(book.getSubject());
        r.setIsbn(book.getIsbn());
        r.setAvailableCopies(copyRepository.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE));
        return r;
    }
}


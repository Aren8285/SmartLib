package com.smartlib.service;

import com.smartlib.dto.BookResponse;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.Book;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.WaitlistStatus;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final WaitlistRepository waitlistRepository;

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

    public BookResponse toResponse(Book book) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setTitle(book.getTitle());
        r.setAuthor(book.getAuthor());
        r.setSubject(book.getSubject());
        r.setIsbn(book.getIsbn());
        r.setAvailableCopies(copyRepository.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE));
        r.setWaitlistSize(waitlistRepository.countByBookIdAndStatus(book.getId(), WaitlistStatus.WAITING));
        return r;
    }
}

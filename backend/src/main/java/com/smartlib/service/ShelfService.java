package com.smartlib.service;

import com.smartlib.dto.BookResponse;
import com.smartlib.dto.ShelfRequestDto;
import com.smartlib.dto.ShelfResponse;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.Book;
import com.smartlib.model.Copy;
import com.smartlib.model.Shelf;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.ShelfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The physical floor is a grid of shelves. A book references at most one shelf;
 * capacity is enforced in books (not copies) so a "full" shelf is easy to reason
 * about and to display on the map.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final BookService bookService;

    public List<ShelfResponse> list() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByRowAscColumnAsc();
        return shelves.stream()
                .sorted(Comparator.comparingInt(Shelf::getRow).thenComparingInt(Shelf::getColumn))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<BookResponse> booksOnShelf(String code) {
        Shelf shelf = requireShelfByCode(code);
        return bookRepository.findByShelfIdOrderByTitleAsc(shelf.getId()).stream()
                .map(bookService::toResponse)
                .collect(Collectors.toList());
    }

    public ShelfResponse create(ShelfRequestDto dto) {
        String code = normalizeCode(dto.getCode());
        if (shelfRepository.existsByCode(code)) {
            throw new BusinessRuleException("A shelf with code \"" + code + "\" already exists.");
        }
        if (shelfRepository.existsByRowAndColumn(dto.getRow(), dto.getColumn())) {
            throw new BusinessRuleException(
                    "Another shelf already occupies row " + dto.getRow() + ", column " + dto.getColumn() + ".");
        }
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .code(code)
                .section(dto.getSection().trim())
                .row(dto.getRow())
                .column(dto.getColumn())
                .capacity(dto.getCapacity())
                .label(trimToNull(dto.getLabel()))
                .build());
        log.info("Shelf {} created ({} row {} col {} capacity {}).",
                shelf.getCode(), shelf.getSection(), shelf.getRow(), shelf.getColumn(), shelf.getCapacity());
        return toResponse(shelf);
    }

    public ShelfResponse update(String id, ShelfRequestDto dto) {
        Shelf shelf = requireShelf(id);
        String code = normalizeCode(dto.getCode());
        shelfRepository.findByCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessRuleException("A shelf with code \"" + code + "\" already exists.");
            }
        });
        List<Shelf> grid = shelfRepository.findAll();
        for (Shelf other : grid) {
            if (!other.getId().equals(id)
                    && other.getRow() == dto.getRow() && other.getColumn() == dto.getColumn()) {
                throw new BusinessRuleException(
                        "Another shelf already occupies row " + dto.getRow() + ", column " + dto.getColumn() + ".");
            }
        }
        long onShelf = bookRepository.countByShelfId(id);
        if (dto.getCapacity() < onShelf) {
            throw new BusinessRuleException(
                    "Capacity cannot be lower than the " + onShelf + " book(s) already on this shelf.");
        }
        shelf.setCode(code);
        shelf.setSection(dto.getSection().trim());
        shelf.setRow(dto.getRow());
        shelf.setColumn(dto.getColumn());
        shelf.setCapacity(dto.getCapacity());
        shelf.setLabel(trimToNull(dto.getLabel()));
        shelf = shelfRepository.save(shelf);
        log.info("Shelf {} updated.", shelf.getCode());
        return toResponse(shelf);
    }

    /** Deleting a non-empty shelf is rejected with a 409. */
    public void delete(String id) {
        Shelf shelf = requireShelf(id);
        long books = bookRepository.countByShelfId(id);
        if (books > 0) {
            throw new BusinessRuleException(
                    "This shelf still holds " + books + " book(s). Move them to another shelf first.");
        }
        shelfRepository.delete(shelf);
        log.info("Shelf {} deleted.", shelf.getCode());
    }

    /** Assigns a book to a shelf (or clears it) and keeps its copies in sync. */
    public BookResponse assignBook(String bookId, String shelfId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        String newShelfId = StringUtils.hasText(shelfId) ? shelfId.trim() : null;
        if (newShelfId != null && !newShelfId.equals(book.getShelfId())) {
            Shelf shelf = requireShelf(newShelfId);
            long onShelf = bookRepository.countByShelfId(shelf.getId());
            if (onShelf >= shelf.getCapacity()) {
                throw new BusinessRuleException(
                        "Shelf " + shelf.getCode() + " is full (" + onShelf + "/" + shelf.getCapacity() + ").");
            }
        }
        book.setShelfId(newShelfId);
        bookRepository.save(book);

        List<Copy> copies = copyRepository.findByBookId(bookId);
        copies.forEach(copy -> copy.setShelfId(newShelfId));
        if (!copies.isEmpty()) {
            copyRepository.saveAll(copies);
        }
        log.info("Book {} assigned to shelf {}.", book.getTitle(), newShelfId == null ? "(none)" : newShelfId);
        return bookService.toResponse(book);
    }

    public Shelf requireShelf(String id) {
        return shelfRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shelf not found: " + id));
    }

    public Shelf requireShelfByCode(String code) {
        return shelfRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new ResourceNotFoundException("Shelf not found: " + code));
    }

    public ShelfResponse toResponse(Shelf shelf) {
        ShelfResponse r = new ShelfResponse();
        r.setId(shelf.getId());
        r.setCode(shelf.getCode());
        r.setSection(shelf.getSection());
        r.setRow(shelf.getRow());
        r.setColumn(shelf.getColumn());
        r.setCapacity(shelf.getCapacity());
        r.setLabel(shelf.getLabel());
        r.setBookCount(bookRepository.countByShelfId(shelf.getId()));
        return r;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

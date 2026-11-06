package com.smartlib.controller;

import com.smartlib.dto.BookResponse;
import com.smartlib.dto.ShelfRequestDto;
import com.smartlib.dto.ShelfResponse;
import com.smartlib.service.ShelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Shelf map data + librarian management. */
@RestController
@RequestMapping("/api/shelves")
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;

    @GetMapping
    public List<ShelfResponse> list() {
        return shelfService.list();
    }

    @GetMapping("/{code}/books")
    public List<BookResponse> booksOnShelf(@PathVariable String code) {
        return shelfService.booksOnShelf(code);
    }

    @PostMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ShelfResponse create(@Valid @RequestBody ShelfRequestDto dto) {
        return shelfService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public ShelfResponse update(@PathVariable String id, @Valid @RequestBody ShelfRequestDto dto) {
        return shelfService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public Map<String, Object> delete(@PathVariable String id) {
        shelfService.delete(id);
        return Map.of("deleted", true);
    }
}

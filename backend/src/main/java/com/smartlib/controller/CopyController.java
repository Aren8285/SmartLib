package com.smartlib.controller;

import com.smartlib.dto.CopyDto;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/copies")
@RequiredArgsConstructor
public class CopyController {

    private final BookService bookService;

    /** Limited manual transitions: AVAILABLE→DAMAGED, AVAILABLE→LOST, DAMAGED→AVAILABLE (repair). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public CopyDto updateStatus(@PathVariable String id, @Valid @RequestBody UpdateCopyStatusDto dto) {
        return bookService.updateCopyStatus(id, dto);
    }
}

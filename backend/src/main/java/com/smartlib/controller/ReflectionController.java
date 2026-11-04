package com.smartlib.controller;

import com.smartlib.dto.ReflectionDto;
import com.smartlib.dto.ReviewReflectionDto;
import com.smartlib.dto.SubmitReflectionDto;
import com.smartlib.model.ReflectionStatus;
import com.smartlib.service.ReflectionService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reflections")
@RequiredArgsConstructor
public class ReflectionController {

    private final ReflectionService reflectionService;
    private final Validator validator;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public ReflectionDto submit(Authentication authentication,
                                @RequestBody SubmitReflectionDto dto) {
        // Re-usable inline validation without Bean validation annotation duplication.
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return reflectionService.submit(authentication.getName(), dto);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ReflectionDto> mine(Authentication authentication) {
        return reflectionService.mine(authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    public List<ReflectionDto> byStatus(@RequestParam(required = false) String status) {
        if (status == null) {
            return reflectionService.byStatus(ReflectionStatus.PENDING);
        }
        return reflectionService.byStatus(ReflectionStatus.valueOf(status.toUpperCase()));
    }

    @GetMapping("/loan/{loanId}")
    public ReflectionDto forLoan(@PathVariable String loanId) {
        return reflectionService.forLoan(loanId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public ReflectionDto approve(@PathVariable String id, Authentication authentication) {
        return reflectionService.approve(id, authentication.getName());
    }

    /** Rejection requires a note; the service also re-checks and raises 409 if blank. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public ReflectionDto reject(@PathVariable String id, Authentication authentication,
                                @RequestBody(required = false) ReviewReflectionDto dto) {
        return reflectionService.reject(id, authentication.getName(),
                dto == null ? null : dto.getReviewNote());
    }
}

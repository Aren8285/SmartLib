package com.smartlib.controller;

import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.ReturnLoanDto;
import com.smartlib.model.LoanStatus;
import com.smartlib.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /** Identity comes from the JWT: a student requests for themselves. */
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public LoanResponse requestLoan(Authentication authentication,
                                    @Valid @RequestBody LoanRequestDto dto) {
        return loanService.requestLoan(authentication.getName(), dto);
    }

    /**
     * studentId filter: accessible to librarians only; students always get their
     * own loans and may never ask for someone else's (403). Librarian can also
     * filter by status.
     */
    @GetMapping
    public List<LoanResponse> getLoans(Authentication authentication,
                                       @RequestParam(required = false) String studentId,
                                       @RequestParam(required = false) String status) {
        // "studentId=me" resolves to the authenticated principal;
        // a student may never query someone else's loans (403).
        if (studentId != null) {
            String resolved = studentId.equals("me") ? authentication.getName() : studentId;
            if (resolved.equals(authentication.getName())) {
                return loanService.getLoansByStudent(resolved);
            }
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only access your own loans.");
        }
        if (status != null) {
            requireRoleLibrarian(authentication);
            return loanService.getLoansByStatus(LoanStatus.valueOf(status.toUpperCase()));
        }
        return List.of();
    }

    private void requireRoleLibrarian(Authentication authentication) {
        boolean librarian = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"));
        if (!librarian) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to perform this action.");
        }
    }

    /** Single enriched loan; a student can only read their own. */
    @GetMapping("/{id}")
    public LoanResponse getLoan(@PathVariable String id, Authentication authentication) {
        LoanResponse loan = loanService.getLoan(id);
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"))
                && !loan.getStudentId().equals(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only access your own loans.");
        }
        return loan;
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanResponse issueLoan(@PathVariable String id) {
        return loanService.issueLoan(id);
    }

    /** bookCondition (GOOD|DAMAGED) is optional in the body. */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanResponse returnLoan(@PathVariable String id,
                                   @RequestBody(required = false) ReturnLoanDto dto) {
        return loanService.returnLoan(id, dto);
    }

    @PostMapping("/{id}/lost")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanResponse lostLoan(@PathVariable String id) {
        return loanService.lostLoan(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public LoanResponse cancelLoan(@PathVariable String id, Authentication authentication) {
        return loanService.cancelLoan(id, authentication.getName());
    }

    @PostMapping("/{id}/skip-reflection")
    @PreAuthorize("hasRole('STUDENT')")
    public LoanResponse skipReflection(@PathVariable String id, Authentication authentication) {
        return loanService.skipReflection(id, authentication.getName());
    }
}

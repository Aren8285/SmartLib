package com.smartlib.controller;

import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.PageResponse;
import com.smartlib.dto.ReturnLoanDto;
import com.smartlib.model.LoanStatus;
import com.smartlib.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
     * Paginated loan lists. Students may only query their own loans; librarians
     * can query one student, a status set, or everything.
     * {@code status} accepts a comma-separated list (e.g. CLOSED,RETURNED).
     * Librarians may also pass {@code q} to search by student name/email, book
     * title/author/ISBN, or copy id.
     */
    @GetMapping
    public PageResponse<LoanResponse> getLoans(Authentication authentication,
                                               @RequestParam(required = false) String studentId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        List<LoanStatus> statuses = parseStatuses(status);
        boolean librarian = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"));
        String resolvedStudent = studentId != null && studentId.equals("me")
                ? authentication.getName() : studentId;

        if (!librarian) {
            if (resolvedStudent != null && !resolvedStudent.equals(authentication.getName())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You can only access your own loans.");
            }
            return loanService.getLoansForStudent(authentication.getName(), statuses, page, size);
        }
        if (resolvedStudent != null) {
            return loanService.getLoansForStudent(resolvedStudent, statuses, page, size);
        }
        return loanService.getLoansForLibrarian(statuses, q, page, size);
    }

    private List<LoanStatus> parseStatuses(String status) {
        List<LoanStatus> statuses = new ArrayList<>();
        if (status == null || status.isBlank()) {
            return statuses;
        }
        for (String part : status.split(",")) {
            if (part.isBlank()) continue;
            try {
                statuses.add(LoanStatus.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown loan status: " + part.trim());
            }
        }
        return statuses;
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

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasRole('STUDENT')")
    public LoanResponse renewLoan(@PathVariable String id, Authentication authentication) {
        return loanService.renewLoan(id, authentication.getName());
    }
}

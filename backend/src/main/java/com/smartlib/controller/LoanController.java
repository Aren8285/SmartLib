package com.smartlib.controller;

import com.smartlib.dto.*;
import com.smartlib.model.LoanStatus;
import com.smartlib.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse requestLoan(@Valid @RequestBody LoanRequestDto dto) {
        return loanService.requestLoan(dto);
    }

    @GetMapping
    public List<LoanResponse> getLoans(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String status) {
        if (studentId != null) {
            return loanService.getLoansByStudent(studentId);
        } else if (status != null) {
            return loanService.getLoansByStatus(LoanStatus.valueOf(status.toUpperCase()));
        }
        return List.of();
    }

    @PostMapping("/{id}/issue")
    public LoanResponse issueLoan(@PathVariable String id, @Valid @RequestBody LibrarianActionDto dto) {
        return loanService.issueLoan(id, dto);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable String id, @Valid @RequestBody LibrarianActionDto dto) {
        return loanService.returnLoan(id, dto);
    }

    @PostMapping("/{id}/lost")
    public LoanResponse lostLoan(@PathVariable String id, @Valid @RequestBody LibrarianActionDto dto) {
        return loanService.lostLoan(id, dto);
    }

    @PostMapping("/{id}/cancel")
    public LoanResponse cancelLoan(@PathVariable String id, @Valid @RequestBody StudentActionDto dto) {
        return loanService.cancelLoan(id, dto);
    }
}

package com.smartlib.controller;

import com.smartlib.dto.FineDto;
import com.smartlib.model.FineStatus;
import com.smartlib.service.FineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @GetMapping
    public List<FineDto> getFines(Authentication authentication,
                                  @RequestParam(required = false) String studentId,
                                  @RequestParam(required = false) String status) {
        if (studentId != null) {
            String resolved = studentId.equals("me") ? authentication.getName() : studentId;
            if (resolved.equals(authentication.getName())) {
                return fineService.listForStudent(resolved);
            }
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only access your own fines.");
        }
        if (status != null) {
            requireRoleLibrarian(authentication);
            return fineService.listByStatus(FineStatus.valueOf(status.toUpperCase()));
        }
        requireRoleLibrarian(authentication);
        return fineService.listAll();
    }

    private void requireRoleLibrarian(Authentication authentication) {
        boolean librarian = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"));
        if (!librarian) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to perform this action.");
        }
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('STUDENT')")
    public FineDto payFine(@PathVariable String id, Authentication authentication) {
        return fineService.pay(id, authentication.getName());
    }
}

package com.smartlib.controller;

import com.smartlib.dto.LibrarianDashboardDto;
import com.smartlib.dto.StudentDashboardDto;
import com.smartlib.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/dashboard/student")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboardDto studentDash(Authentication authentication) {
        return dashboardService.forStudent(authentication.getName());
    }

    @GetMapping("/api/dashboard/librarian")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LibrarianDashboardDto librarianDash() {
        return dashboardService.forLibrarian();
    }
}

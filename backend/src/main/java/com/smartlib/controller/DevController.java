package com.smartlib.controller;

import com.smartlib.dto.LoanResponse;
import com.smartlib.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev/testing endpoints to simulate the passage of time without waiting for
 * the real schedules. Active only when the Spring profile "dev" is on
 * (it is the default active profile for this iteration) AND the caller is a
 * librarian.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevController {

    private final LoanService loanService;

    @PostMapping("/loans/{id}/set-due-date")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanResponse setDueDate(@PathVariable String id,
                                   @RequestParam(defaultValue = "0") long daysFromNow) {
        return loanService.devSetDueDate(id, daysFromNow);
    }

    @PostMapping("/loans/{id}/set-pickup-deadline")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanResponse setPickupDeadline(@PathVariable String id,
                                          @RequestParam(defaultValue = "0") long minutesFromNow) {
        return loanService.devSetPickupDeadline(id, minutesFromNow);
    }

    @PostMapping("/run-jobs")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public Map<String, Integer> runJobs() {
        int expired = loanService.runPickupExpiryJob();
        int overdue = loanService.runOverdueJob();
        int reminders = loanService.runReminderJobs();
        return Map.of("expiredLoans", expired, "overdueLoans", overdue, "remindersSent", reminders);
    }
}

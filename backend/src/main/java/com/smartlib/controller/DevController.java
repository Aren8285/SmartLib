package com.smartlib.controller;

import com.smartlib.dto.InvariantViolation;
import com.smartlib.dto.LoanResponse;
import com.smartlib.service.InvariantService;
import com.smartlib.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dev/testing endpoints to simulate the passage of time and inspect data
 * consistency. Active only when the Spring profile "dev" is on AND the caller
 * is a librarian.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevController {

    private final LoanService loanService;
    private final InvariantService invariantService;

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

    /** Consistency checker used by the demo and by tests. */
    @GetMapping("/invariants")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public Map<String, Object> invariants() {
        List<InvariantViolation> violations = invariantService.check();
        return Map.of("violations", violations, "count", violations.size());
    }

    /** Repairs the "waiting students + available copy" stuck-queue case. */
    @PostMapping("/repair")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public Map<String, Integer> repair() {
        int repaired = invariantService.repairStuckQueues();
        return Map.of("repairedCopies", repaired);
    }
}

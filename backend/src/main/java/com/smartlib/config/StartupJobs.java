package com.smartlib.config;

import com.smartlib.service.InvariantService;
import com.smartlib.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Catch-up after downtime: on startup both scheduled jobs run once so pickups
 * that expired while the process was asleep are processed and the queue is
 * promoted in the same order the scheduler would have used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupJobs {

    private final LoanService loanService;
    private final InvariantService invariantService;

    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        try {
            int expired = loanService.runPickupExpiryJob();
            int overdue = loanService.runOverdueJob();
            int reminders = loanService.runReminderJobs();
            log.info("Startup catch-up complete: {} expired, {} overdue, {} reminder(s).",
                    expired, overdue, reminders);
            invariantService.logCheck();
        } catch (Exception e) {
            log.error("Startup catch-up failed; the scheduled jobs will retry.", e);
        }
    }
}

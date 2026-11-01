package com.smartlib.config;

import com.smartlib.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanScheduler {

    private final LoanService loanService;

    @Scheduled(fixedDelayString = "${app.scheduler.expiry-check-ms:60000}")
    public void expiryCheck() {
        int expired = loanService.runPickupExpiryJob();
        if (expired > 0) {
            log.info("Scheduled expiry job: {} loan(s) expired.", expired);
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.overdue-check-ms:60000}")
    public void overdueCheck() {
        int overdue = loanService.runOverdueJob();
        if (overdue > 0) {
            log.info("Scheduled overdue job: {} loan(s) marked overdue.", overdue);
        }
    }
}

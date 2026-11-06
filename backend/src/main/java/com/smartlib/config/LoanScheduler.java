package com.smartlib.config;

import com.smartlib.service.InvariantService;
import com.smartlib.service.LoanService;
import com.smartlib.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanScheduler {

    private final LoanService loanService;
    private final InvariantService invariantService;
    private final NotificationService notificationService;
    private final AppProperties app;

    @Scheduled(fixedDelayString = "${app.scheduler.expiry-check-ms:60000}")
    public void expiryCheck() {
        try {
            int expired = loanService.runPickupExpiryJob();
            if (expired > 0) {
                log.info("Scheduled expiry job: {} loan(s) expired.", expired);
            }
        } catch (Exception e) {
            log.error("Scheduled expiry job crashed; will retry next cycle.", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.overdue-check-ms:60000}")
    public void overdueCheck() {
        try {
            int overdue = loanService.runOverdueJob();
            if (overdue > 0) {
                log.info("Scheduled overdue job: {} loan(s) marked overdue.", overdue);
            }
        } catch (Exception e) {
            log.error("Scheduled overdue job crashed; will retry next cycle.", e);
        }
    }

    /** One-shot reminders: pickup < 6h away, due date < 24h away. */
    @Scheduled(fixedDelayString = "${app.scheduler.reminder-check-ms:300000}")
    public void reminderCheck() {
        try {
            int sent = loanService.runReminderJobs();
            if (sent > 0) {
                log.info("Scheduled reminder job: {} notification(s) sent.", sent);
            }
        } catch (Exception e) {
            log.error("Scheduled reminder job crashed; will retry next cycle.", e);
        }
    }

    /** Daily self-check; logs warnings so drift is visible before it breaks the demo. */
    @Scheduled(cron = "0 0 4 * * *")
    public void invariantSelfCheck() {
        try {
            invariantService.logCheck();
        } catch (Exception e) {
            log.error("Invariant self-check failed.", e);
        }
    }

    /** Weekly notification retention cleanup. */
    @Scheduled(cron = "0 30 3 * * MON")
    public void notificationCleanup() {
        try {
            notificationService.purgeOlderThanDays(app.getNotificationRetentionDays());
        } catch (Exception e) {
            log.error("Notification cleanup failed.", e);
        }
    }
}

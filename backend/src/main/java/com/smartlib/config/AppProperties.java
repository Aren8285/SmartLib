package com.smartlib.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private long pickupDeadlineHours = 48;
    private long loanDays = 14;
    private int finePerDay = 5;
    private int lostBookFine = 500;
    private int onTimeReturnPoints = 10;
    private int maxActiveLoans = 3;
    private int maxWaitlistEntries = 5;

    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Scheduler {
        private long expiryCheckMs = 60000;
        private long overdueCheckMs = 60000;
    }
}

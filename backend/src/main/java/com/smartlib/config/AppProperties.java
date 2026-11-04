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

    // Iteration 3
    private int reflectionPoints = 20;
    private int damagedBookFine = 200;
    private int recommendationLimit = 8;
    private int reflectionMinLength = 50;
    private Jwt jwt = new Jwt();

    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Scheduler {
        private long expiryCheckMs = 60000;
        private long overdueCheckMs = 60000;
        private long reminderCheckMs = 300000;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMinutes = 480;
    }
}

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

    // Iteration 4
    private boolean seedDemo = true;
    private int notificationRetentionDays = 90;
    private Jwt jwt = new Jwt();
    private Scheduler scheduler = new Scheduler();
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();

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

    @Data
    public static class Cors {
        /** Comma-separated list of allowed browser origins. */
        private String allowedOrigins = "http://localhost:5173";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int attemptsPerMinute = 10;
    }
}

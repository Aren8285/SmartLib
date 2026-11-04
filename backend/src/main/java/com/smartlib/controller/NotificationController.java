package com.smartlib.controller;

import com.smartlib.model.Notification;
import com.smartlib.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Own notifications only; the userId always comes from the JWT. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<Notification> list(Authentication authentication,
                                   @RequestParam(required = false) Boolean unreadOnly) {
        return notificationService.listForUser(
                authentication.getName(),
                Boolean.TRUE.equals(unreadOnly));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.unreadCount(authentication.getName()));
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable String id, Authentication authentication) {
        notificationService.markRead(id, authentication.getName());
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication authentication) {
        return Map.of("marked", notificationService.markAllRead(authentication.getName()));
    }
}

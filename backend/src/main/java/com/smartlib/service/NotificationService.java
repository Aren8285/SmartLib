package com.smartlib.service;

import com.smartlib.model.Notification;
import com.smartlib.model.NotificationType;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.repository.NotificationRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Single funnel for all in-app notifications. Transitions notify through this
 * class so message formats stay consistent and future channels (email etc.)
 * have one place to hook into.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public Notification create(String userId, NotificationType type, String title, String message, String relatedLoanId) {
        if (userId == null) {
            return null;
        }
        return notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .createdAt(Instant.now())
                .relatedLoanId(relatedLoanId)
                .build());
    }

    /** Fan-out to every active librarian (e.g. a new reflection awaits review). */
    public void createForLibrarians(NotificationType type, String title, String message, String relatedLoanId) {
        userRepository.findByRoleAndActiveTrue(Role.LIBRARIAN).forEach(librarian ->
                create(librarian.getId(), type, title, message, relatedLoanId));
    }

    public List<Notification> listForUser(String userId, boolean unreadOnly) {
        return unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(String notificationId, String userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.smartlib.exception.ResourceNotFoundException(
                        "Notification not found: " + notificationId));
        if (!n.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not your notification");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    public int markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}

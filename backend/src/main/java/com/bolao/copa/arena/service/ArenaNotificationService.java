package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.NotificationResponse;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.NotificationType;
import com.bolao.copa.arena.repository.ArenaNotificationRepository;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaNotificationService {
    private final ArenaNotificationRepository notifications;
    private final com.bolao.copa.arena.repository.PlayerProfileRepository profiles;
    public ArenaNotificationService(ArenaNotificationRepository notifications,
                                    com.bolao.copa.arena.repository.PlayerProfileRepository profiles) {
        this.notifications = notifications; this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User user) {
        return notifications.findTop100ByUserOrderByCreatedAtDesc(user).stream().map(this::response).toList();
    }
    public long unread(User user) { return notifications.countByUserAndReadAtIsNull(user); }

    @Transactional
    public NotificationResponse read(Long id, User user) {
        ArenaNotification notification = notifications.findByIdAndUser(id, user)
                .orElseThrow(() -> new ArenaProblem.NotFound("Notificação não encontrada."));
        if (notification.getReadAt() == null) notification.setReadAt(Instant.now());
        return response(notification);
    }
    @Transactional public int readAll(User user) { return notifications.markAllRead(user, Instant.now()); }

    public ArenaNotification create(User user, NotificationType type, String title, String message, String targetUrl) {
        if (type != NotificationType.ADMIN_NOTICE
                && profiles.findByUser(user).filter(profile -> !profile.isNotificationsEnabled()).isPresent()) return null;
        ArenaNotification value = new ArenaNotification();
        value.setUser(user);
        value.setType(type);
        value.setTitle(title);
        value.setMessage(message);
        value.setTargetUrl(targetUrl);
        return notifications.save(value);
    }
    private NotificationResponse response(ArenaNotification value) {
        return new NotificationResponse(value.getId(), value.getType(), value.getTitle(), value.getMessage(),
                value.getTargetUrl(), value.getCreatedAt(), value.getReadAt() != null);
    }
}

package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.ArenaNotification;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ArenaNotificationRepository extends JpaRepository<ArenaNotification, Long> {
    @Override @EntityGraph(attributePaths = "user")
    org.springframework.data.domain.Page<ArenaNotification> findAll(org.springframework.data.domain.Pageable pageable);
    List<ArenaNotification> findTop100ByUserOrderByCreatedAtDesc(User user);
    Optional<ArenaNotification> findByIdAndUser(Long id, User user);
    long countByUserAndReadAtIsNull(User user);
    @Modifying @Query("update ArenaNotification n set n.readAt = :readAt where n.user = :user and n.readAt is null")
    int markAllRead(User user, Instant readAt);
}

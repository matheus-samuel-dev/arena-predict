package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.entity.User;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    Optional<UserAchievement> findByUserAndAchievement(User user, AchievementDefinition achievement);
    @EntityGraph(attributePaths = "achievement") List<UserAchievement> findByUserOrderByUnlockedAtDesc(User user);
}

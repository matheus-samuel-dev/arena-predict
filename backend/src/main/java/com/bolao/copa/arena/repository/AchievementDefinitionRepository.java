package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.AchievementDefinition;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AchievementDefinitionRepository extends JpaRepository<AchievementDefinition, Long> {
    Optional<AchievementDefinition> findByCode(String code);
    List<AchievementDefinition> findByActiveTrueOrderByIdAsc();
}

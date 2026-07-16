package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.ChallengeDefinition;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChallengeDefinitionRepository extends JpaRepository<ChallengeDefinition, Long> {
    Optional<ChallengeDefinition> findByCode(String code);
    List<ChallengeDefinition> findByActiveTrueOrderByExpiresAtAsc();
}

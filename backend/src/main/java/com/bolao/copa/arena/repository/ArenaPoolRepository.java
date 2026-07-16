package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.ArenaPool;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ArenaPoolRepository extends JpaRepository<ArenaPool, Long> {
    @Override @EntityGraph(attributePaths = {"sport", "championship", "owner"}) List<ArenaPool> findAll();
    @Override @EntityGraph(attributePaths = {"sport", "championship", "owner"}) Page<ArenaPool> findAll(Pageable pageable);
    @EntityGraph(attributePaths = {"sport", "championship", "owner"})
    Page<ArenaPool> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<ArenaPool> findByInviteCodeIgnoreCase(String code);
    boolean existsByInviteCode(String code);
}

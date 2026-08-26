package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.ArenaPool;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ArenaPoolRepository extends JpaRepository<ArenaPool, Long> {
    @Override @EntityGraph(attributePaths = {"sport", "championship", "owner"}) List<ArenaPool> findAll();
    @Override @EntityGraph(attributePaths = {"sport", "championship", "owner"}) Page<ArenaPool> findAll(Pageable pageable);
    @EntityGraph(attributePaths = {"sport", "championship", "owner"})
    Page<ArenaPool> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<ArenaPool> findByInviteCodeIgnoreCase(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sport", "championship", "owner"})
    @Query("select pool from ArenaPool pool where pool.id = :id")
    Optional<ArenaPool> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sport", "championship", "owner"})
    @Query("select pool from ArenaPool pool where lower(pool.inviteCode) = lower(:code)")
    Optional<ArenaPool> findByInviteCodeIgnoreCaseForUpdate(@Param("code") String code);
    boolean existsByInviteCode(String code);
}

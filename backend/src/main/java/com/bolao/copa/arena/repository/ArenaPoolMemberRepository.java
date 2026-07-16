package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.entity.User;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface ArenaPoolMemberRepository extends JpaRepository<ArenaPoolMember, Long> {
    interface PoolMemberCount {
        Long getPoolId();
        long getTotal();
    }

    Optional<ArenaPoolMember> findByPoolAndUser(ArenaPool pool, User user);
    long countByPool(ArenaPool pool);
    List<ArenaPoolMember> findByPool(ArenaPool pool);
    long countByUser(User user);
    @Query("select distinct member.user from ArenaPoolMember member where member.pool in " +
            "(select mine.pool from ArenaPoolMember mine where mine.user = :user)")
    List<User> findUsersSharingPoolWith(User user);
    @Query("select member.pool.id as poolId, count(member) as total from ArenaPoolMember member " +
            "where member.pool in :pools group by member.pool.id")
    List<PoolMemberCount> countGrouped(Collection<ArenaPool> pools);
}

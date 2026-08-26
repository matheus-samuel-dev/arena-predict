package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ArenaEventRepository extends JpaRepository<ArenaEvent, Long> {
    @Override @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    List<ArenaEvent> findAll();
    @Override @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    Page<ArenaEvent> findAll(Pageable pageable);
    @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    @Query("select event from ArenaEvent event where " +
            "lower(event.title) like lower(concat('%', :search, '%')) or " +
            "lower(event.externalKey) like lower(concat('%', :search, '%')) or " +
            "lower(event.championship.name) like lower(concat('%', :search, '%')) or " +
            "lower(event.championship.sport.name) like lower(concat('%', :search, '%'))")
    Page<ArenaEvent> search(@Param("search") String search, Pageable pageable);
    @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    Optional<ArenaEvent> findByExternalKey(String externalKey);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ArenaEvent event where event.externalKey = :externalKey")
    Optional<ArenaEvent> findByExternalKeyForUpdate(@Param("externalKey") String externalKey);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ArenaEvent event where event.id = :id")
    Optional<ArenaEvent> findByIdForUpdate(@Param("id") Long id);
    @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    List<ArenaEvent> findByStatusOrderByStartsAtAsc(EventStatus status);
    @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    List<ArenaEvent> findTop12ByStartsAtAfterAndStatusInOrderByStartsAtAsc(Instant startsAt, Collection<EventStatus> statuses);
    long countByStatus(EventStatus status);
    @Query("select count(distinct event) from ArenaEvent event join PredictionMarket market on market.event = event " +
            "where event.status = :eventStatus and market.status <> :settledStatus")
    long countAwaitingMarketSettlement(EventStatus eventStatus, MarketStatus settledStatus);
}

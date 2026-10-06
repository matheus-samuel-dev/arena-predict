package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ArenaEventRepository extends JpaRepository<ArenaEvent, Long>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<ArenaEvent> {
    @Override @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    Page<ArenaEvent> findAll(org.springframework.data.jpa.domain.Specification<ArenaEvent> specification,Pageable pageable);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"championship", "championship.sport", "homeCompetitor", "awayCompetitor"})
    Optional<ArenaEvent> findByExternalProviderAndExternalId(String externalProvider, String externalId);
    long countByExternalProviderAndResultReviewRequiredTrue(String externalProvider);
    @Query("select e.externalId from ArenaEvent e where e.externalProvider = :provider and e.externalId in :ids")
    List<String> findExistingExternalIds(@Param("provider") String provider, @Param("ids") Collection<String> ids);
    @Query("select e.externalId from ArenaEvent e where e.externalProvider = :provider and e.resultReviewRequired = false and " +
            "((e.resultProcessedAt is null and e.status = :finished and coalesce(e.finishedAt, e.startsAt) >= :oldest) or " +
            "(e.status not in :terminal " +
            "and e.startsAt <= :near and e.startsAt >= :oldest)) order by e.lastSyncedAt asc, e.id asc")
    List<String> findTrackedExternalIds(@Param("provider") String provider, @Param("near") Instant near,
                                       @Param("oldest") Instant oldest, @Param("finished") EventStatus finished,
                                       @Param("terminal") Collection<EventStatus> terminal, Pageable pageable);
    @Query("select e.externalId from ArenaEvent e where e.externalProvider = :provider " +
            "and e.resultReviewRequired = false and e.resultProcessedAt is null and e.status = :finished " +
            "and coalesce(e.finishedAt, e.startsAt) < :oldest order by coalesce(e.finishedAt, e.startsAt), e.id")
    List<String> findExpiredIncompleteExternalIds(@Param("provider") String provider, @Param("oldest") Instant oldest,
                                                @Param("finished") EventStatus finished, Pageable pageable);
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
    boolean existsByChampionship(Championship championship);
    boolean existsByHomeCompetitorOrAwayCompetitor(Competitor homeCompetitor, Competitor awayCompetitor);
    @Query("select count(event) > 0 from ArenaEvent event where event.demoManaged = true " +
            "and (event.homeCompetitor = :competitor or event.awayCompetitor = :competitor)")
    boolean isControlledDemoCompetitor(@Param("competitor") Competitor competitor);
    long countByStatus(EventStatus status);
    @Query("select count(distinct event) from ArenaEvent event join PredictionMarket market on market.event = event " +
            "where event.status = :eventStatus and market.status <> :settledStatus")
    long countAwaitingMarketSettlement(EventStatus eventStatus, MarketStatus settledStatus);
}

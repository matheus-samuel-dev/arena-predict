package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface PredictionMarketRepository extends JpaRepository<PredictionMarket, Long> {
    @Query("select m.event.id from PredictionMarket m where m.id = :id")
    Optional<Long> eventIdForMarket(@Param("id") Long id);
    @Override @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport"}) Page<PredictionMarket> findAll(Pageable pageable);
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport"})
    @Query("select market from PredictionMarket market where " +
            "lower(market.name) like lower(concat('%', :search, '%')) or " +
            "lower(market.code) like lower(concat('%', :search, '%')) or " +
            "lower(market.event.externalKey) like lower(concat('%', :search, '%')) or " +
            "lower(market.event.title) like lower(concat('%', :search, '%'))")
    Page<PredictionMarket> search(@Param("search") String search, Pageable pageable);
    List<PredictionMarket> findByEventOrderByIdAsc(ArenaEvent event);
    List<PredictionMarket> findByEventInOrderByEventIdAscIdAsc(Collection<ArenaEvent> events);
    Optional<PredictionMarket> findByEventAndCode(ArenaEvent event, String code);
    long countByStatus(MarketStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select market from PredictionMarket market join fetch market.event where market.id = :id")
    Optional<PredictionMarket> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select market from PredictionMarket market where market.event = :event order by market.id")
    List<PredictionMarket> findByEventForUpdate(@Param("event") ArenaEvent event);
}

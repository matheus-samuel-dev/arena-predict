package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ArenaPredictionRepository extends JpaRepository<ArenaPrediction, Long> {
    interface CommandContext { Long getEventId(); Long getMarketId(); }
    @Query("select p.event.id as eventId, p.market.id as marketId from ArenaPrediction p where p.id = :id and p.user = :user")
    Optional<CommandContext> commandContext(@Param("id") Long id, @Param("user") User user);
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport", "market", "option", "pool"})
    List<ArenaPrediction> findByUserOrderByPlacedAtDesc(User user);
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport", "market", "option", "pool"})
    Optional<ArenaPrediction> findByIdAndUser(Long id, User user);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport", "market", "option", "pool"})
    @Query("select prediction from ArenaPrediction prediction where prediction.id = :id and prediction.user = :user")
    Optional<ArenaPrediction> findByIdAndUserForUpdate(@Param("id") Long id, @Param("user") User user);
    @EntityGraph(attributePaths = {"event", "market", "option", "user"})
    List<ArenaPrediction> findByMarketAndStatus(PredictionMarket market, PredictionStatus status);
    @EntityGraph(attributePaths = {"event", "market", "option", "user"})
    List<ArenaPrediction> findByEventAndStatus(ArenaEvent event, PredictionStatus status);
    Optional<ArenaPrediction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByMarket(PredictionMarket market);
    long countByUserAndStatus(User user, PredictionStatus status);
    long countByStatus(PredictionStatus status);
    List<ArenaPrediction> findByPool(ArenaPool pool);
    @EntityGraph(attributePaths = {"user", "event", "event.championship", "event.championship.sport"})
    @Query("select prediction from ArenaPrediction prediction " +
            "where prediction.resolvedAt >= :since " +
            "order by prediction.resolvedAt desc, prediction.id desc")
    List<ArenaPrediction> findForRankingSince(Instant since);
}

package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ArenaPredictionRepository extends JpaRepository<ArenaPrediction, Long> {
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport", "market", "option", "pool"})
    List<ArenaPrediction> findByUserOrderByPlacedAtDesc(User user);
    @EntityGraph(attributePaths = {"event", "event.championship", "event.championship.sport", "market", "option", "pool"})
    Optional<ArenaPrediction> findByIdAndUser(Long id, User user);
    @EntityGraph(attributePaths = {"event", "market", "option", "user"})
    List<ArenaPrediction> findByMarketAndStatus(PredictionMarket market, PredictionStatus status);
    @EntityGraph(attributePaths = {"event", "market", "option", "user"})
    List<ArenaPrediction> findByEventAndStatus(ArenaEvent event, PredictionStatus status);
    Optional<ArenaPrediction> findByIdempotencyKey(String idempotencyKey);
    long countByUserAndStatus(User user, PredictionStatus status);
    long countByStatus(PredictionStatus status);
    List<ArenaPrediction> findByPool(ArenaPool pool);
    @EntityGraph(attributePaths = {"user", "event", "event.championship", "event.championship.sport"})
    @Query("select prediction from ArenaPrediction prediction where prediction.placedAt >= :since order by prediction.placedAt desc")
    List<ArenaPrediction> findForRankingSince(Instant since);
}

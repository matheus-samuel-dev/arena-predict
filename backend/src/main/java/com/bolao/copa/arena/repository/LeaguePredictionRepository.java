package com.bolao.copa.arena.repository;

import com.bolao.copa.arena.domain.ArenaPool;
import com.bolao.copa.arena.domain.ArenaPrediction;
import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** A league counts eligible member predictions automatically, across social pools. */
public interface LeaguePredictionRepository extends Repository<ArenaPrediction, Long> {
    @EntityGraph(attributePaths = {"user", "event", "event.championship", "event.championship.sport"})
    @Query("select prediction from ArenaPrediction prediction, ArenaPoolMember member " +
            "where member.pool = :pool and member.user = prediction.user " +
            "and prediction.placedAt >= member.joinedAt " +
            "and prediction.placedAt >= member.pool.startsAt and prediction.placedAt < member.pool.endsAt " +
            "and (member.pool.sport is null or prediction.event.championship.sport = member.pool.sport) " +
            "and (member.pool.championship is null or prediction.event.championship = member.pool.championship) " +
            "and prediction.status in :statuses")
    List<ArenaPrediction> findEligible(@Param("pool") ArenaPool pool,
                                      @Param("statuses") List<PredictionStatus> statuses);
}

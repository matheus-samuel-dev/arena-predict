package com.bolao.copa.arena.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Separate read-only transaction prevents uncommitted fixtures/results from leaking into the model cache. */
@Repository
public class ConfirmedSeriesHistoryReader {
    private final JdbcTemplate db;
    public ConfirmedSeriesHistoryReader(JdbcTemplate db) { this.db=db; }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public List<BradleyTerryStrengthModel.Result> read() {
        return db.query("""
            select * from (
            select e.external_id,s.code,h.external_id as home_key,a.external_id as away_key,
                   e.home_score,e.away_score,e.best_of,e.starts_at,
                   coalesce(e.finished_at,e.result_processed_at) as finished,e.result_processed_at,e.championship_id
              from arena_events e join arena_championships c on c.id=e.championship_id
              join arena_sports s on s.id=c.sport_id
              join arena_competitors h on h.id=e.home_competitor_id join arena_competitors a on a.id=e.away_competitor_id
             where e.external_provider='PANDASCORE' and e.demo=false and e.status='FINISHED'
               and e.result_review_required=false and e.result_processed_at is not null
               and e.home_score is not null and e.away_score is not null and e.best_of in (1,3,5)
               and e.winner_external_id=case when e.home_score>e.away_score then h.external_id else a.external_id end
            union all
            select r.external_id,r.sport,r.home_id,r.away_id,r.home_score,r.away_score,r.best_of,r.starts_at,
                   r.ended_at,r.observed_at,cast(r.championship_external_id as bigint)
              from arena_sports_results_history r
             where r.provider='PANDASCORE'
               and not exists(select 1 from arena_events e where e.external_provider=r.provider and e.external_id=r.external_id)
            ) confirmed order by finished desc,external_id desc limit 10000
            """,(r,n)->new BradleyTerryStrengthModel.Result(r.getString("external_id"),r.getString("code"),r.getString("home_key"),r.getString("away_key"),
                r.getInt("home_score"),r.getInt("away_score"),r.getInt("best_of"),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("finished").toInstant(),
                r.getTimestamp("result_processed_at").toInstant(),r.getLong("championship_id")));
    }
}

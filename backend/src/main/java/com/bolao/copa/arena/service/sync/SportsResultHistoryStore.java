package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.service.HistoricalTeamStrengthService;
import com.bolao.copa.arena.service.provider.SportsMatch;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Archive has no user data or operational event lifecycle. Existing events remain authoritative. */
@Repository
public class SportsResultHistoryStore {
    private final JdbcTemplate db;
    private final HistoricalTeamStrengthService cache;
    public record Saved(int inserted,int updated,int unchanged) { }
    public SportsResultHistoryStore(JdbcTemplate db,HistoricalTeamStrengthService cache) { this.db=db;this.cache=cache; }
    @Transactional
    public Saved save(String provider,List<SportsMatch> matches,Instant observedAt) {
        if(!"PANDASCORE".equals(provider))throw new IllegalArgumentException("Unsupported history owner");
        int inserted=0,updated=0,unchanged=0;
        for(var match:matches) {
            String fingerprint=fingerprint(match);
            var existing=db.queryForList("select fingerprint from arena_sports_results_history where provider=? and external_id=?",provider,match.externalId());
            if(!existing.isEmpty()&&fingerprint.equals(existing.getFirst().get("fingerprint"))) { unchanged++;continue; }
            if(existing.isEmpty()) {
                db.update("insert into arena_sports_results_history(sport,home_id,away_id,championship_external_id,home_score,away_score,best_of,"+
                        "starts_at,ended_at,observed_at,fingerprint,provider,external_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",values(provider,match,observedAt,fingerprint));
                inserted++;
            } else {
                db.update("update arena_sports_results_history set sport=?,home_id=?,away_id=?,championship_external_id=?,home_score=?,away_score=?,best_of=?,"+
                        "starts_at=?,ended_at=?,observed_at=?,fingerprint=? where provider=? and external_id=?",values(provider,match,observedAt,fingerprint));
                updated++;
            }
        }
        if(inserted+updated>0)cache.invalidateAfterCommit();
        return new Saved(inserted,updated,unchanged);
    }
    private static Object[] values(String provider,SportsMatch m,Instant observed,String fingerprint) {
        return new Object[]{m.sportCode(),m.homeTeam().externalId(),m.awayTeam().externalId(),m.championship().externalId(),m.homeScore(),m.awayScore(),m.bestOf(),
                Timestamp.from(m.scheduledAt()),Timestamp.from(m.endedAt()),Timestamp.from(observed),fingerprint,provider,m.externalId()};
    }
    private static String fingerprint(SportsMatch m) {
        String content=m.sportCode()+"|"+m.externalId()+"|"+m.homeTeam().externalId()+"|"+m.awayTeam().externalId()+"|"+
                m.championship().externalId()+"|"+m.homeScore()+"|"+m.awayScore()+"|"+m.bestOf()+"|"+m.scheduledAt()+"|"+m.endedAt();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

package com.bolao.copa.arena.service.sync;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Journal only. The caller owns the existing provider lease; event commits precede cursor advancement. */
@Repository
public class SportsHistoryStateStore {
    public record Progress(String provider,String sport,Instant targetAt,Instant windowEndAt,int nextPage,
            boolean completed,Instant lastAttemptAt,Instant lastSuccessAt,long received,long accepted,long rejected,
            String fingerprint,String lastError) { }
    private final JdbcTemplate db;
    public SportsHistoryStateStore(JdbcTemplate db) { this.db=db; }

    public void initialize(String provider,String sport,Instant target,Instant until) {
        if(!progress(provider).stream().anyMatch(p -> p.sport().equals(sport))) {
            try { db.update("insert into arena_sports_history_backfill(provider,sport,target_at,window_end_at) values (?,?,?,?)",
                    provider,sport,Timestamp.from(target),Timestamp.from(until)); }
            catch(DuplicateKeyException concurrentInitializer) { /* Provider lease normally prevents this. */ }
        }
    }
    public List<Progress> progress(String provider) {
        return db.query("select * from arena_sports_history_backfill where provider=? order by sport",(r,n)->
                new Progress(r.getString("provider"),r.getString("sport"),time(r,"target_at"),time(r,"window_end_at"),r.getInt("next_page"),
                        r.getBoolean("completed"),time(r,"last_attempt_at"),time(r,"last_success_at"),r.getLong("received_count"),
                        r.getLong("accepted_count"),r.getLong("rejected_count"),r.getString("last_page_fingerprint"),r.getString("last_error")),provider);
    }
    public void attempted(Progress p,Instant at) {
        db.update("update arena_sports_history_backfill set last_attempt_at=? where provider=? and sport=?",Timestamp.from(at),p.provider(),p.sport());
    }
    public void failed(Progress p,String reason) {
        db.update("update arena_sports_history_backfill set last_error=? where provider=? and sport=?",reason,p.provider(),p.sport());
    }
    public void completed(Progress p,Instant from,boolean next,int received,int accepted,String fingerprint,Instant at) {
        Instant end=next?p.windowEndAt():from;
        db.update("update arena_sports_history_backfill set window_end_at=?,next_page=?,completed=?,last_success_at=?,"+
                "received_count=received_count+?,accepted_count=accepted_count+?,rejected_count=rejected_count+?,"+
                "last_page_fingerprint=?,last_error=null where provider=? and sport=? and window_end_at=? and next_page=?",
                Timestamp.from(end),next?p.nextPage()+1:1,!next&&!end.isAfter(p.targetAt()),Timestamp.from(at),received,accepted,received-accepted,
                next?fingerprint:null,p.provider(),p.sport(),Timestamp.from(p.windowEndAt()),p.nextPage());
    }
    private static Instant time(ResultSet r,String column) throws SQLException {
        Timestamp value=r.getTimestamp(column);return value==null?null:value.toInstant();
    }
}

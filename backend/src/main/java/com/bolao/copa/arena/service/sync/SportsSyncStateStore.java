package com.bolao.copa.arena.service.sync;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Short DB operations only. The expiring lease survives application restarts. */
@Repository
public class SportsSyncStateStore {
    public record Snapshot(Instant lastAttemptAt, Instant lastSuccessAt, String status, String message) { }
    public enum Feed { UPCOMING, RUNNING, FINISHED, TRACKED }
    private final JdbcTemplate jdbc;
    public SportsSyncStateStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean claim(String provider, String owner, Instant now, Instant until) {
        return jdbc.update("update arena_sports_sync_state set lease_owner=?, lease_until=?, last_attempt_at=? " +
                "where provider=? and (lease_until is null or lease_until < ?)", owner, Timestamp.from(until),
                Timestamp.from(now), provider, Timestamp.from(now)) == 1;
    }
    public void release(String provider, String owner) {
        jdbc.update("update arena_sports_sync_state set lease_owner=null, lease_until=null where provider=? and lease_owner=?", provider, owner);
    }
    public void lock(String provider) {
        jdbc.queryForObject("select provider from arena_sports_sync_state where provider=? for update", String.class, provider);
    }
    public Instant lastFeed(String provider, Feed feed) {
        return jdbc.queryForObject("select " + column(feed) + " from arena_sports_sync_state where provider=?",
                (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), provider);
    }
    public void completed(String provider, Feed feed, Instant now) {
        jdbc.update("update arena_sports_sync_state set " + column(feed) + "=?, last_success_at=? where provider=?",
                Timestamp.from(now), Timestamp.from(now), provider);
    }
    public void checkedWithoutRequest(String provider, Feed feed, Instant now) {
        jdbc.update("update arena_sports_sync_state set " + column(feed) + "=? where provider=?",Timestamp.from(now),provider);
    }
    public void status(String provider, String status, String message) {
        jdbc.update("update arena_sports_sync_state set status=?, message=? where provider=?", status, message, provider);
    }
    public Snapshot snapshot(String provider) {
        return jdbc.queryForObject("select last_attempt_at, last_success_at, status, message from arena_sports_sync_state where provider=?",
                (rs,row) -> new Snapshot(rs.getTimestamp(1)==null?null:rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant(),rs.getString(3),rs.getString(4)),provider);
    }
    private String column(Feed feed) {
        return switch(feed) {
            case UPCOMING -> "upcoming_at";
            case RUNNING -> "running_at";
            case FINISHED -> "finished_at";
            case TRACKED -> "tracked_at";
        };
    }
}

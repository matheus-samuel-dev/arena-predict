package com.bolao.copa.arena.service.sync;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Short DB operations only. The expiring lease survives application restarts. */
@Repository
public class SportsSyncStateStore {
    public record Snapshot(Instant lastAttemptAt, Instant lastSuccessAt, String status, String message,
            Instant runningAt, Instant upcomingAt, Instant finishedAt, Instant trackedAt,
            Instant schedulerTickAt, Instant runCompletedAt, Instant retryAfterAt,
            int received, int inserted, int updated, int skipped, int failed,
            Long durationMs, Integer lastHttpStatus, String lastErrorReason) {
        public Snapshot(Instant lastAttemptAt, Instant lastSuccessAt, String status, String message) {
            this(lastAttemptAt,lastSuccessAt,status,message,null,null,null,null,null,null,null,0,0,0,0,0,null,null,null);
        }
    }
    public enum Feed { UPCOMING, RUNNING, FINISHED, TRACKED }
    private final JdbcTemplate jdbc;
    public SportsSyncStateStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean claim(String provider, String owner, Instant now, Instant until) {
        return jdbc.update("update arena_sports_sync_state set lease_owner=?, lease_until=? " +
                "where provider=? and (lease_until is null or lease_until < ?)", owner, Timestamp.from(until),
                provider, Timestamp.from(now)) == 1;
    }
    public void schedulerTick(String provider, Instant now) {
        jdbc.update("update arena_sports_sync_state set scheduler_tick_at=? where provider=?",Timestamp.from(now),provider);
    }
    public void attempted(String provider, Instant now) {
        jdbc.update("update arena_sports_sync_state set last_attempt_at=?, status='SYNCING' where provider=?",Timestamp.from(now),provider);
    }
    public void runCompleted(String provider, Instant now, String status, String message, Instant retryAt,
            int received, int inserted, int updated, int skipped, int failed, long durationMs,
            Integer lastHttpStatus, String lastErrorReason) {
        jdbc.update("update arena_sports_sync_state set run_completed_at=?, status=?, message=?, retry_after_at=?, " +
                "received_count=?, inserted_count=?, updated_count=?, skipped_count=?, failed_count=?, duration_ms=?, " +
                "last_http_status=?, last_error_reason=? where provider=?",Timestamp.from(now),status,message,
                retryAt==null?null:Timestamp.from(retryAt),received,inserted,updated,skipped,failed,durationMs,
                lastHttpStatus,lastErrorReason,provider);
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
        return jdbc.queryForObject("select * from arena_sports_sync_state where provider=?",
                (rs,row) -> new Snapshot(time(rs,"last_attempt_at"),time(rs,"last_success_at"),rs.getString("status"),rs.getString("message"),
                        time(rs,"running_at"),time(rs,"upcoming_at"),time(rs,"finished_at"),time(rs,"tracked_at"),
                        time(rs,"scheduler_tick_at"),time(rs,"run_completed_at"),time(rs,"retry_after_at"),
                        rs.getInt("received_count"),rs.getInt("inserted_count"),rs.getInt("updated_count"),rs.getInt("skipped_count"),rs.getInt("failed_count"),
                        rs.getObject("duration_ms",Long.class),rs.getObject("last_http_status",Integer.class),rs.getString("last_error_reason")),provider);
    }
    private static Instant time(java.sql.ResultSet rs,String column) throws java.sql.SQLException {
        var value=rs.getTimestamp(column); return value==null?null:value.toInstant();
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

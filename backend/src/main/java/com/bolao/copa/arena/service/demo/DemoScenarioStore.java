package com.bolao.copa.arena.service.demo;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One durable row coordinates start, result, reset and maintenance across replicas. */
@Repository
public class DemoScenarioStore {
    public record State(long generation, Long championshipId, Long activeEventId, Long historyEventId, Instant updatedAt) { }
    private final JdbcTemplate jdbc;
    public DemoScenarioStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public State read() { return query(false); }
    public State lock() { return query(true); }
    private State query(boolean lock) {
        return jdbc.queryForObject("select * from arena_demo_scenario where id = 1" + (lock ? " for update" : ""),
                (rs, row) -> new State(rs.getLong("generation"), (Long) rs.getObject("championship_id"),
                        (Long) rs.getObject("active_event_id"), (Long) rs.getObject("history_event_id"),
                        rs.getTimestamp("updated_at").toInstant()));
    }
    public void save(long generation, Long championship, Long active, Long history) {
        jdbc.update("update arena_demo_scenario set generation=?, championship_id=?, active_event_id=?, history_event_id=?, updated_at=CURRENT_TIMESTAMP where id=1",
                generation, championship, active, history);
    }
    public void touch() { jdbc.update("update arena_demo_scenario set updated_at=CURRENT_TIMESTAMP where id=1"); }
}

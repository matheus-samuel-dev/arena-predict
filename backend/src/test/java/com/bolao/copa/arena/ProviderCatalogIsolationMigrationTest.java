package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exercises an upgrade with pre-existing mixed references, not an empty schema alone. */
class ProviderCatalogIsolationMigrationTest {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void baseline() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:catalog-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        migrate("11");
        jdbc.update("insert into arena_sports(id,code,name,category) values(100,'CS2','Counter-Strike 2','ESPORTS')");
        jdbc.update("insert into arena_championships(id,sport_id,name,slug,season,status,external_provider,external_id) "
                + "values(100,100,'Official tournament','official','2026','ACTIVE','PANDASCORE','100')");
        jdbc.update("insert into arena_championships(id,sport_id,name,slug,season,status) "
                + "values(200,100,'Demo tournament','demo','2026','ACTIVE')");
        jdbc.update("insert into arena_competitors(id,sport_id,name,code,external_provider,external_id) "
                + "values(100,100,'Official home','PS_HOME','PANDASCORE','101'),(101,100,'Official away','PS_AWAY','PANDASCORE','102')");
        jdbc.update("insert into arena_competitors(id,sport_id,name,code) "
                + "values(200,100,'Demo home','DEMO_HOME'),(201,100,'Demo away','DEMO_AWAY')");
    }

    @Test
    void upgradeCopiesOnlyCatalogReferencesOfInternalEventsAndKeepsOfficialResultsUntouched() {
        event(100, 100, 100, 101, false, "PANDASCORE", "real");
        event(200, 100, 100, 101, true, null, "demo");
        jdbc.update("update arena_events set status='FINISHED',home_score=2,away_score=1,result_data='preserved-result' where id in (100,200)");
        jdbc.update("insert into arena_event_participants(event_id,competitor_id,display_order) values(100,100,0),(200,100,0),(200,101,1)");
        Map<String, Object> officialBefore = jdbc.queryForMap("select * from arena_events where id=100");
        Map<String, Object> championshipBefore = jdbc.queryForMap("select * from arena_championships where id=100");
        Map<String, Object> homeBefore = jdbc.queryForMap("select * from arena_competitors where id=100");

        migrate("12");

        Map<String, Object> officialAfter = jdbc.queryForMap("select * from arena_events where id=100");
        officialAfter.remove("provider_owned");
        assertThat(officialAfter).isEqualTo(officialBefore);
        Map<String, Object> championshipAfter = jdbc.queryForMap("select * from arena_championships where id=100");
        championshipAfter.remove("provider_owned");
        assertThat(championshipAfter).isEqualTo(championshipBefore);
        Map<String, Object> homeAfter = jdbc.queryForMap("select * from arena_competitors where id=100");
        homeAfter.remove("provider_owned");
        assertThat(homeAfter).isEqualTo(homeBefore);

        assertThat(jdbc.queryForObject("select championship_id from arena_events where id=200", Long.class)).isNotEqualTo(100L);
        assertThat(jdbc.queryForObject("select home_competitor_id from arena_events where id=200", Long.class)).isNotEqualTo(100L);
        assertThat(jdbc.queryForObject("select count(*) from arena_events e join arena_championships c on c.id=e.championship_id "
                + "join arena_competitors h on h.id=e.home_competitor_id join arena_competitors a on a.id=e.away_competitor_id "
                + "where e.id=200 and c.external_provider is null and h.external_provider is null and a.external_provider is null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from arena_event_participants p join arena_competitors c on c.id=p.competitor_id "
                + "where p.event_id=200 and c.external_provider is null and not p.provider_owned", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("select home_score,away_score,result_data from arena_events where id=200"))
                .containsEntry("home_score", 2).containsEntry("away_score", 1).containsEntry("result_data", "preserved-result");
        assertThat(jdbc.queryForObject("select count(*) from arena_events", Integer.class)).isEqualTo(2);
    }

    @Test
    void databaseRejectsMixedCatalogEvenWhenApplicationValidationIsBypassed() {
        event(100, 100, 100, 101, false, "PANDASCORE", "real");
        event(200, 200, 200, 201, true, null, "demo");
        migrate("12");

        assertRejected("update arena_events set championship_id=100 where id=200");
        assertRejected("update arena_events set home_competitor_id=100 where id=200");
        assertRejected("update arena_events set away_competitor_id=201 where id=100");
        assertRejected("update arena_events set provider_owned=true where id=200");
        assertRejected("update arena_events set demo=true where id=100");
        assertRejected("update arena_competitors set provider_owned=false where id=100");
        assertRejected("insert into arena_event_participants(event_id,competitor_id,display_order,provider_owned) values(200,100,0,false)");
        assertRejected("insert into arena_event_participants(event_id,competitor_id,display_order,provider_owned) values(200,100,0,true)");
        jdbc.update("insert into arena_event_participants(event_id,competitor_id,display_order,provider_owned) values(100,100,0,true),(200,200,0,false)");
        assertThat(jdbc.queryForObject("select count(*) from arena_event_participants", Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidOfficialReferencesStopBeforeRepairInsteadOfInventingProviderIdentities() {
        event(100, 200, 200, 201, false, "PANDASCORE", "invalid-real");
        assertThatThrownBy(() -> migrate("12")).hasStackTraceContaining("Restore verified provider references");
        assertThat(jdbc.queryForObject("select championship_id from arena_events where id=100", Long.class)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='arena_events' "
                + "and column_name='provider_owned'", Integer.class)).isZero();
    }

    @Test
    void competitorCodeBasedResultsRequireReviewRatherThanAnUnsafeAutomaticRewrite() {
        event(200, 200, 100, 101, true, null, "mixed-race");
        jdbc.update("update arena_events set format='RACE' where id=200");
        assertThatThrownBy(() -> migrate("12")).hasStackTraceContaining("automatic repair supports head-to-head events only");
        assertThat(jdbc.queryForObject("select home_competitor_id from arena_events where id=200", Long.class)).isEqualTo(100);
    }

    private void assertRejected(String statement) {
        assertThatThrownBy(() -> jdbc.update(statement)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void migrate(String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private void event(long id, long championship, long home, long away, boolean demo, String provider, String key) {
        jdbc.update("insert into arena_events(id,external_key,championship_id,home_competitor_id,away_competitor_id,title,"
                        + "starts_at,prediction_closes_at,status,format,best_of,demo,external_provider,external_id) "
                        + "values(?,?,?,?,?,'Fixture',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'SCHEDULED','BO3',3,?,?,?)",
                id, key, championship, home, away, demo, provider, provider == null ? null : key);
    }
}

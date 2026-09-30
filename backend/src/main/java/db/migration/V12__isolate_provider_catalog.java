package db.migration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Portable PostgreSQL/H2 data repair and ownership constraints; no sporting result is rewritten. */
public class V12__isolate_provider_catalog extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection=context.getConnection();
        // Fail before DDL/data changes if existing real identities would have to be invented.
        requireNoRows(connection,"select e.id from arena_events e join arena_championships c on c.id=e.championship_id "
                + "left join arena_competitors h on h.id=e.home_competitor_id left join arena_competitors a on a.id=e.away_competitor_id "
                + "where e.external_provider is not null and (c.external_provider is null "
                + "or (h.id is not null and h.external_provider is null) or (a.id is not null and a.external_provider is null))",
                "External event references an internal catalog. Restore verified provider references before retrying V12; no external identity can be invented.");
        requireNoRows(connection,"select p.event_id from arena_event_participants p join arena_events e on e.id=p.event_id "
                + "join arena_competitors c on c.id=p.competitor_id where e.external_provider is not null and c.external_provider is null",
                "External event participant references an internal competitor. Restore verified provider references before retrying V12.");
        requireNoRows(connection,"select e.id from arena_events e where e.external_provider is null and e.format in ('RACE','INDIVIDUAL') and ("
                + "exists (select 1 from arena_competitors c where c.external_provider is not null and (c.id=e.home_competitor_id or c.id=e.away_competitor_id)) "
                + "or exists (select 1 from arena_event_participants p join arena_competitors c on c.id=p.competitor_id "
                + "where p.event_id=e.id and c.external_provider is not null))",
                "Internal RACE/INDIVIDUAL event references external competitors. Review competitor-code result/market references before retrying V12; automatic repair supports head-to-head events only.");

        execute(connection,"alter table arena_championships add column provider_owned boolean not null default false");
        execute(connection,"alter table arena_competitors add column provider_owned boolean not null default false");
        execute(connection,"alter table arena_events add column provider_owned boolean not null default false");
        execute(connection,"alter table arena_event_participants add column provider_owned boolean not null default false");

        // Older demo ranking seeds chose the first championship without checking its provider.
        // Keep the official rows intact and copy only metadata needed by existing internal events.
        for (long id:ids(connection,"select distinct c.id from arena_championships c join arena_events e on e.championship_id=c.id "
                + "where c.external_provider is not null and e.external_provider is null")) {
            String slug=unusedKey(connection,"arena_championships","slug","local-copy-"+id,120,id);
            long copy=copy(connection,"insert into arena_championships(sport_id,name,slug,season,status,image_url,starts_at,ends_at,league_name,series_name) "
                    + "select sport_id,name,?,season,status,image_url,starts_at,ends_at,league_name,series_name from arena_championships where id=?",slug,id);
            update(connection,"update arena_events set championship_id=? where championship_id=? and external_provider is null",copy,id);
        }
        for (long id:ids(connection,"select c.id from arena_competitors c where c.external_provider is not null and ("
                + "exists (select 1 from arena_events e where e.external_provider is null and (e.home_competitor_id=c.id or e.away_competitor_id=c.id)) "
                + "or exists (select 1 from arena_event_participants p join arena_events e on e.id=p.event_id "
                + "where p.competitor_id=c.id and e.external_provider is null))")) {
            String code=unusedKey(connection,"arena_competitors","code","LOCAL_COPY_"+id,30,id);
            long copy=copy(connection,"insert into arena_competitors(sport_id,name,code,image_url,country,active,acronym) "
                    + "select sport_id,name,?,image_url,country,active,acronym from arena_competitors where id=?",code,id);
            update(connection,"update arena_events set home_competitor_id=? where home_competitor_id=? and external_provider is null",copy,id);
            update(connection,"update arena_events set away_competitor_id=? where away_competitor_id=? and external_provider is null",copy,id);
            update(connection,"update arena_event_participants set competitor_id=? where competitor_id=? "
                    + "and event_id in (select id from arena_events where external_provider is null)",copy,id);
        }

        for (String table:List.of("arena_championships","arena_competitors","arena_events")) {
            execute(connection,"update "+table+" set provider_owned=(external_provider is not null)");
            execute(connection,"alter table "+table+" add constraint ck_"+table+"_ownership check (provider_owned=(external_provider is not null))");
            execute(connection,"alter table "+table+" add constraint uq_"+table+"_ownership unique(id,provider_owned)");
        }
        execute(connection,"update arena_event_participants set provider_owned=(select e.provider_owned from arena_events e where e.id=event_id)");
        foreignKey(connection,"arena_events","championship_id","arena_championships","fk_arena_event_champ_ownership",false);
        foreignKey(connection,"arena_events","home_competitor_id","arena_competitors","fk_arena_event_home_ownership",false);
        foreignKey(connection,"arena_events","away_competitor_id","arena_competitors","fk_arena_event_away_ownership",false);
        foreignKey(connection,"arena_event_participants","event_id","arena_events","fk_arena_participant_event_ownership",true);
        foreignKey(connection,"arena_event_participants","competitor_id","arena_competitors","fk_arena_participant_team_ownership",false);
    }

    private static void foreignKey(Connection connection,String table,String column,String parent,String name,boolean cascade) throws SQLException {
        execute(connection,"alter table "+table+" add constraint "+name+" foreign key("+column+",provider_owned) references "
                +parent+"(id,provider_owned)"+(cascade?" on delete cascade":""));
    }
    private static void requireNoRows(Connection connection,String sql,String message) throws SQLException {
        try (Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery(sql)) {
            if (rows.next()) throw new SQLException(message+" Event ID: "+rows.getLong(1));
        }
    }
    private static List<Long> ids(Connection connection,String sql) throws SQLException {
        List<Long> result=new ArrayList<>();
        try (Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery(sql)) {
            while (rows.next()) result.add(rows.getLong(1));
        }
        return result;
    }
    private static String unusedKey(Connection connection,String table,String column,String base,int maximum,long sourceId) throws SQLException {
        for (int suffix=0;;suffix++) {
            String ending=suffix==0?"":"_"+suffix;
            String value=base.substring(0,Math.min(base.length(),maximum-ending.length()))+ending;
            try (PreparedStatement query=connection.prepareStatement("select id from "+table+" where "+column+"=? and sport_id=(select sport_id from "+table+" where id=?)")) {
                query.setString(1,value); query.setLong(2,sourceId);
                try (ResultSet rows=query.executeQuery()) { if (!rows.next()) return value; }
            }
        }
    }
    private static long copy(Connection connection,String sql,String key,long source) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1,key); statement.setLong(2,source);
            if (statement.executeUpdate()!=1) throw new SQLException("Source catalog row disappeared while isolating provider data");
            try (ResultSet keys=statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated catalog identity returned");
                return keys.getLong(1);
            }
        }
    }
    private static void update(Connection connection,String sql,long target,long source) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(sql)) {
            statement.setLong(1,target); statement.setLong(2,source); statement.executeUpdate();
        }
    }
    private static void execute(Connection connection,String sql) throws SQLException {
        try (Statement statement=connection.createStatement()) { statement.execute(sql); }
    }
}

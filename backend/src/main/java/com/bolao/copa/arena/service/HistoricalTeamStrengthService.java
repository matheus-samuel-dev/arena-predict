package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.ArenaEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

/** Bounded cache of confirmed sports data only. No provider calls, user data or clock-driven odds updates. */
@Service
public class HistoricalTeamStrengthService {
    public static final int MINIMUM_SERIES=5,WINDOW_DAYS=30;
    public static final double PRIOR_PRECISION=4,HALF_LIFE_DAYS=14;
    public record Strength(BradleyTerryStrengthModel.Estimate estimate,String revision,Instant asOf) { }
    private final ConfirmedSeriesHistoryReader reader;
    private List<BradleyTerryStrengthModel.Result> cached=List.of();
    private Instant expires=Instant.EPOCH;
    private boolean ready;
    private final Map<String,BradleyTerryStrengthModel> models=new LinkedHashMap<>(32,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,BradleyTerryStrengthModel> entry) { return size()>64; }
    };
    public HistoricalTeamStrengthService(ConfirmedSeriesHistoryReader reader) { this.reader=reader; }
    public synchronized Strength estimate(ArenaEvent event) {
        Instant now=Instant.now(),asOf=event.getLastSyncedAt()==null?now:event.getLastSyncedAt();
        try {
            if(!ready)return neutral("HISTORY_NOT_READY",asOf);
            String sport=event.getChampionship().getSport().getCode(),home=event.getHomeCompetitor().getExternalId(),away=event.getAwayCompetitor().getExternalId();
            if(home==null||away==null) return neutral("TEAM_IDENTITY_UNAVAILABLE",asOf);
            var eligible=cached.stream().filter(r->sport.equals(r.sport())&&!Objects.equals(r.id(),event.getExternalId())
                    && BradleyTerryStrengthModel.valid(r)&&!r.finishedAt().isAfter(asOf)&&!r.confirmedAt().isAfter(asOf)
                    && !r.finishedAt().isBefore(asOf.minus(WINDOW_DAYS,ChronoUnit.DAYS))).toList();
            var sameFormat=eligible.stream().filter(r->Objects.equals(r.bestOf(),event.getBestOf())).toList();
            var chosen=fit(sameFormat,asOf);var result=chosen.estimate(home,away,MINIMUM_SERIES);
            String revision=revision(sameFormat,asOf);
            var limitations=new ArrayList<>(result.limitations());
            if("SYMMETRIC_PRIOR".equals(result.source())) {
                chosen=fit(eligible,asOf);result=chosen.estimate(home,away,MINIMUM_SERIES);revision=revision(eligible,asOf);
                limitations=new ArrayList<>(result.limitations());limitations.add("FORMAT_POOLED");
            }
            return new Strength(new BradleyTerryStrengthModel.Estimate(result.mapProbability(),result.source(),result.confidence(),result.homeSamples(),result.awaySamples(),
                    result.headToHeadSamples(),List.copyOf(limitations)),revision,asOf);
        } catch(DataAccessException failure) { return neutral("HISTORY_TEMPORARILY_UNAVAILABLE",asOf); }
    }
    /** Runs outside visitor/provider command transactions: no REQUIRES_NEW pool starvation. */
    public void refresh() {
        List<BradleyTerryStrengthModel.Result> loaded;
        try { loaded=reader.read(); }
        catch(DataAccessException unavailable) { synchronized(this) { ready=false;expires=Instant.now().plusSeconds(30); }return; }
        synchronized(this) { cached=List.copyOf(loaded);ready=true;expires=Instant.now().plusSeconds(30); }
    }
    public synchronized boolean refreshDue() { return !Instant.now().isBefore(expires); }
    private BradleyTerryStrengthModel fit(List<BradleyTerryStrengthModel.Result> rows,Instant asOf) {
        String key=revision(rows,asOf);
        return models.computeIfAbsent(key,k->new BradleyTerryStrengthModel(rows,asOf,PRIOR_PRECISION,HALF_LIFE_DAYS));
    }
    public static Strength neutral(String reason,Instant asOf) { return new Strength(BradleyTerryStrengthModel.Estimate.neutral(reason),"symmetric-prior-v1",asOf); }
    public static String revision(List<BradleyTerryStrengthModel.Result> rows,Instant asOf) {
        String value=asOf.atOffset(ZoneOffset.UTC).toLocalDate()+"|"+rows.stream().sorted(Comparator.comparing(BradleyTerryStrengthModel.Result::id)).toList();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public void invalidateAfterCommit() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { invalidate(); }
        }); else invalidate();
    }
    private synchronized void invalidate() { expires=Instant.EPOCH; }
}

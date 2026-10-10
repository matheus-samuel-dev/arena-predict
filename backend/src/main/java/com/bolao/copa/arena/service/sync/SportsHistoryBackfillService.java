package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.config.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.provider.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;

/** One persisted page per tick; no new provider, pricing engine, user request or parallel sync. */
@Service
public class SportsHistoryBackfillService {
    private static final Logger log=LoggerFactory.getLogger(SportsHistoryBackfillService.class);
    public record Window(SportsHistoryStateStore.Progress progress,Instant from,Instant until) { }
    public record Batch(List<SportsMatch> matches,int received,boolean next,String fingerprint) { }
    private final SportsHistoryProperties properties;
    private final SportsSyncProperties sync;
    private final SportsHistoryStateStore state;
    private final SportsResultHistoryStore results;
    public SportsHistoryBackfillService(SportsHistoryProperties properties,SportsSyncProperties sync,SportsHistoryStateStore state,SportsResultHistoryStore results) {
        this.properties=properties;this.sync=sync;this.state=state;this.results=results;
    }
    public Window nextWindow(SportsDataProvider provider,Instant now) {
        if(!properties.enabled()||provider.demo()||!provider.supportsHistoricalBackfill())return null;
        Instant target=now.minusSeconds(properties.days()*86400L),until=now.minusSeconds(sync.correctionWindowHours()*3600L);
        if(!target.isBefore(until))return null;
        for(String sport:properties.sports()) if(provider.supportedSports().contains(sport))state.initialize(provider.providerId(),sport,target,until);
        return state.progress(provider.providerId()).stream().filter(p->properties.sports().contains(p.sport())&&!p.completed()
                        && (p.lastAttemptAt()==null||!now.isBefore(p.lastAttemptAt().plusMillis(properties.intervalMs()))))
                .min(Comparator.comparing(p->p.lastAttemptAt()==null?Instant.EPOCH:p.lastAttemptAt())).map(p->{
                    Instant from=p.windowEndAt().minusSeconds(86400);if(from.isBefore(p.targetAt()))from=p.targetAt();
                    return new Window(p,from,p.windowEndAt());
                }).orElse(null);
    }
    public Batch fetch(SportsDataProvider provider,Window window,Instant now) {
        var p=window.progress();state.attempted(p,now);
        var page=provider.historicalPage(p.sport(),window.from(),window.until(),p.nextPage());
        if(page.received()>0&&p.nextPage()>1&&Objects.equals(p.fingerprint(),page.fingerprint()))
            throw new SportsProviderException(SportsProviderException.Reason.INVALID_RESPONSE,"Historical page did not advance",null);
        var accepted=page.matches().stream().filter(m->eligible(m,p.sport(),window.from(),window.until())).toList();
        return new Batch(accepted,page.received(),page.hasNext(),page.fingerprint());
    }
    public void completed(Window window,Batch batch,Instant now) {
        var saved=results.save(window.progress().provider(),batch.matches(),now);
        state.completed(window.progress(),window.from(),batch.next(),batch.received(),batch.matches().size(),batch.fingerprint(),now);
        log.info("[SPORTS_HISTORY] sport={} page={} received={} accepted={} rejected={} nextPage={} windowFrom={} windowUntil={} inserted={} updated={} unchanged={}",
                window.progress().sport(),window.progress().nextPage(),batch.received(),batch.matches().size(),batch.received()-batch.matches().size(),
                batch.next(),window.from(),window.until(),saved.inserted(),saved.updated(),saved.unchanged());
    }
    public void failed(Window window,RuntimeException failure) {
        state.failed(window.progress(),failure instanceof SportsProviderException p?p.getReason().name():"IMPORT_FAILED");
    }
    public List<SportsHistoryStateStore.Progress> progress(String provider) { return state.progress(provider); }
    static boolean eligible(SportsMatch m,String sport,Instant from,Instant until) {
        if(m.status()!=EventStatus.FINISHED||!sport.equals(m.sportCode())||m.homeTeam()==null||m.awayTeam()==null||m.championship()==null
                ||m.homeTeam().externalId()==null||m.awayTeam().externalId()==null||m.homeTeam().externalId().equals(m.awayTeam().externalId())
                ||m.championship().externalId()==null||!m.championship().externalId().matches("[1-9][0-9]{0,17}")
                ||m.scheduledAt()==null||m.endedAt()==null||m.endedAt().isBefore(from)||m.endedAt().isAfter(until)
                ||m.scheduledAt().isAfter(m.endedAt())||m.forfeit()||m.draw()||m.bestOf()==null||!List.of(1,3,5).contains(m.bestOf())
                ||m.homeScore()==null||m.awayScore()==null)return false;
        int target=m.bestOf()/2+1;
        return Math.max(m.homeScore(),m.awayScore())==target&&Math.min(m.homeScore(),m.awayScore())>=0
                &&Math.min(m.homeScore(),m.awayScore())<target
                &&Objects.equals(m.winnerExternalId(),m.homeScore()>m.awayScore()?m.homeTeam().externalId():m.awayTeam().externalId());
    }
}

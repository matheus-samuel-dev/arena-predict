package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.SportsSyncStateStore.Feed;
import com.bolao.copa.arena.service.sync.SportsSyncStateStore.Snapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Bounded feeds outside transactions; one atomic transaction per persisted match. */
@Service
public class SportsSyncService {
    private static final Logger log=LoggerFactory.getLogger(SportsSyncService.class);
    private final SportsDataProvider provider;
    private final SportsSyncProperties properties;
    private final SportsSyncStateStore state;
    private final SportsCatalogSyncService catalog;
    private final SportsMatchSyncService matches;
    private final ArenaEventRepository events;
    private final Clock clock;
    private final Instant startedAt;

    @Autowired
    public SportsSyncService(List<SportsDataProvider> providers,SportsSyncProperties properties,
            SportsSyncStateStore state,SportsCatalogSyncService catalog,SportsMatchSyncService matches,ArenaEventRepository events) {
        this(providers,properties,state,catalog,matches,events,Clock.systemUTC());
    }
    SportsSyncService(List<SportsDataProvider> providers,SportsSyncProperties properties,
            SportsSyncStateStore state,SportsCatalogSyncService catalog,SportsMatchSyncService matches,ArenaEventRepository events,Clock clock) {
        this.properties=properties; this.state=state; this.catalog=catalog; this.matches=matches; this.events=events; this.clock=clock;
        this.startedAt=clock.instant();
        this.provider=providers.stream().filter(p -> !p.demo() && p.providerId().equalsIgnoreCase(properties.provider()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown sports.sync.provider"));
    }
    public void scheduledSynchronize() {
        state.schedulerTick(provider.providerId(),clock.instant());
        synchronize();
    }
    public void synchronize() {
        if (!properties.enabled() || !provider.available()) return;
        Instant now=clock.instant(), clientRetry=provider.nextAllowedRequestAt();
        if (clientRetry!=null && now.isBefore(clientRetry)) return;
        Instant retryAt=retryAt(snapshot());
        if (retryAt!=null && now.isBefore(retryAt)) return;
        String owner=UUID.randomUUID().toString(), id=provider.providerId();
        if (!state.claim(id,owner,now,now.plusMillis(properties.leaseMs()))) return;
        Run run=new Run(); run.startedAt=now;
        String result="ONLINE", message="Sincronização concluída; dados persistidos disponíveis.", error=null;
        Instant failureRetryAt=null;
        try {
            feed(Feed.RUNNING,properties.runningIntervalMs(),now,run,() -> request(run,provider::runningMatches));
            feed(Feed.TRACKED,properties.trackedIntervalMs(),now,run,() -> {
                Instant oldest=now.minus(properties.correctionWindowHours(),ChronoUnit.HOURS);
                matches.quarantineExpiredResults(id,oldest,properties.trackedBatchSize());
                List<String> ids=events.findTrackedExternalIds(id,now.plus(properties.nearStartMinutes(),ChronoUnit.MINUTES),
                        oldest,EventStatus.FINISHED,List.of(EventStatus.FINISHED,EventStatus.CANCELLED),PageRequest.of(0,properties.trackedBatchSize()));
                return ids.isEmpty()?null:request(run,() -> provider.matchDetails(ids));
            });
            feed(Feed.UPCOMING,properties.upcomingIntervalMs(),now,run,
                    () -> request(run,() -> provider.upcomingMatches(now,now.plus(properties.upcomingDays(),ChronoUnit.DAYS))));
            feed(Feed.FINISHED,properties.finishedIntervalMs(),now,run,
                    () -> request(run,() -> provider.finishedMatches(now.minus(properties.correctionWindowHours(),ChronoUnit.HOURS))));
        } catch (SportsProviderException ex) {
            result=ex.getReason()==SportsProviderException.Reason.RATE_LIMITED?"RATE_LIMITED":"UNAVAILABLE";
            message=safeMessage(ex); error=ex.getReason().name();
            failureRetryAt=ex.getRetryAt();
            log.warn("[SPORTS_SYNC] Provider unavailable provider={} reason={} retryAt={}; keeping persisted data",id,ex.getReason(),ex.getRetryAt());
        } catch (RuntimeException ex) {
            result="UNAVAILABLE"; message="Falha ao sincronizar; os últimos dados salvos continuam disponíveis."; error="SYNC_ERROR";
            log.error("[SPORTS_SYNC] Synchronization failed provider={} errorType={}",id,ex.getClass().getSimpleName());
        } finally {
            try {
                if (run.attempted || error!=null) {
                    run.updated.removeAll(run.inserted);
                    long duration=Math.max(0,Duration.between(run.startedAt,clock.instant()).toMillis());
                    Instant pause=provider.nextAllowedRequestAt();
                    if (failureRetryAt!=null && (pause==null || failureRetryAt.isAfter(pause))) pause=failureRetryAt;
                    state.runCompleted(id,clock.instant(),result,message,pause,run.received,
                            run.inserted.size(),run.updated.size(),run.skipped.size(),run.failed.size(),duration,provider.lastHttpStatus(),error);
                    log.info("[SPORTS_SYNC] Finished provider={} received={} inserted={} updated={} skipped={} failed={} durationMs={} status={}",
                            id,run.received,run.inserted.size(),run.updated.size(),run.skipped.size(),run.failed.size(),duration,result);
                }
            } finally { state.release(id,owner); }
        }
    }
    private List<SportsMatch> request(Run run,Supplier<List<SportsMatch>> fetch) {
        if (!run.attempted) {
            run.startedAt=clock.instant();
            log.info("[SPORTS_SYNC] Started provider={}",provider.providerId());
        }
        run.attempted=true;
        state.attempted(provider.providerId(),clock.instant());
        return fetch.get();
    }
    private void feed(Feed feed,long interval,Instant now,Run run,Supplier<List<SportsMatch>> fetch) {
        Instant last=state.lastFeed(provider.providerId(),feed);
        if (last!=null && now.isBefore(last.plusMillis(interval))) return;
        List<SportsMatch> snapshots=fetch.get();
        if (snapshots==null) { state.checkedWithoutRequest(provider.providerId(),feed,now); return; }
        run.received+=snapshots.size();
        log.info("[SPORTS_SYNC] Feed={} received={}",feed,snapshots.size());
        var refs=catalog.synchronize(provider.providerId(),snapshots);
        Set<String> existing=snapshots.isEmpty()?Set.of():new HashSet<>(events.findExistingExternalIds(provider.providerId(),
                snapshots.stream().map(SportsMatch::externalId).toList()));
        boolean failed=false;
        for (SportsMatch snapshot:snapshots) {
            try {
                if (!matches.synchronize(provider.providerId(),snapshot,refs)) run.skipped.add(snapshot.externalId());
                else if (existing.contains(snapshot.externalId())) run.updated.add(snapshot.externalId());
                else run.inserted.add(snapshot.externalId());
            } catch (RuntimeException ex) {
                failed=true; run.failed.add(snapshot.externalId());
                log.error("[SPORTS_SYNC] Match transaction rolled back externalId={} errorType={}",snapshot.externalId(),ex.getClass().getSimpleName());
            }
        }
        if (failed) throw new IllegalStateException("One or more sports match transactions failed");
        state.completed(provider.providerId(),feed,clock.instant());
    }
    public Status status() {
        var saved=snapshot();
        Instant now=clock.instant(), retry=retryAt(saved);
        String status=!properties.enabled()?"DISABLED":!provider.available()?"UNCONFIGURED":saved.status();
        String message=!properties.enabled()?"Sincronização externa desativada.":!provider.available()?
                "PANDASCORE_API_TOKEN não configurado. Os dados salvos e o modo Demo continuam disponíveis.":saved.message();
        if (properties.enabled() && provider.available()) {
            if (retry!=null && now.isBefore(retry) && !"UNAVAILABLE".equals(status)) {
                status="RATE_LIMITED"; message="Quota protegida; novas consultas aguardam a janela permitida pelo provedor.";
            } else if (saved.lastAttemptAt()==null) {
                status="CONFIGURED"; message="Credencial presente; aguardando a primeira consulta ao provedor.";
            } else if ("ONLINE".equals(status) && (saved.lastSuccessAt()==null
                    || saved.lastSuccessAt().isBefore(now.minusMillis(properties.runningIntervalMs()*3))
                    || (saved.schedulerTickAt()!=null && saved.schedulerTickAt().isBefore(now.minusMillis(properties.intervalMs()*3))))) {
                status="DEGRADED"; message="Sem sincronização recente confirmada. Últimos dados salvos preservados.";
            }
        }
        return new Status(provider.providerName(),properties.enabled(),provider.available(),status,saved.lastAttemptAt(),saved.lastSuccessAt(),
                retry,provider.remainingRequests(),message,events.countByExternalProviderAndResultReviewRequiredTrue(provider.providerId()),
                provider.supportedSports(),saved.schedulerTickAt(),nextSyncAt(saved,retry),saved.runCompletedAt(),saved.received(),saved.inserted(),
                saved.updated(),saved.skipped(),saved.failed(),saved.durationMs(),saved.lastHttpStatus(),saved.lastErrorReason());
    }
    public Summary summary() {
        var status=status();
        return new Summary("ONLINE".equals(status.status()) && status.lastSuccessAt()!=null,status.lastSuccessAt());
    }
    private Snapshot snapshot() {
        var saved=state.snapshot(provider.providerId());
        return saved==null?new Snapshot(null,null,"UNCONFIGURED",null):saved;
    }
    private Instant retryAt(Snapshot saved) {
        var live=provider.nextAllowedRequestAt();
        return saved.retryAfterAt()==null?live:live==null?saved.retryAfterAt():saved.retryAfterAt().isAfter(live)?saved.retryAfterAt():live;
    }
    private Instant nextSyncAt(Snapshot saved,Instant retry) {
        if (!properties.enabled() || !provider.available()) return null;
        Instant tick=saved.schedulerTickAt()==null?startedAt.plusMillis(properties.initialDelayMs()):saved.schedulerTickAt().plusMillis(properties.intervalMs());
        if (tick.isBefore(clock.instant())) tick=clock.instant().plusMillis(properties.intervalMs());
        Instant due=Collections.min(List.of(due(saved.runningAt(),properties.runningIntervalMs()),due(saved.upcomingAt(),properties.upcomingIntervalMs()),
                due(saved.finishedAt(),properties.finishedIntervalMs()),due(saved.trackedAt(),properties.trackedIntervalMs())));
        if (retry!=null && retry.isAfter(due)) due=retry;
        if (!due.isAfter(tick)) return tick;
        long delay=Duration.between(tick,due).toMillis();
        return tick.plusMillis(((delay+properties.intervalMs()-1)/properties.intervalMs())*properties.intervalMs());
    }
    private Instant due(Instant last,long interval) { return last==null?startedAt:last.plusMillis(interval); }
    public record Summary(boolean healthy,Instant lastSuccessAt) { }
    public record Status(String provider,boolean enabled,boolean configured,String status,Instant lastAttemptAt,
            Instant lastSuccessAt,Instant nextAllowedRequestAt,Long requestsRemaining,String message,long reviewRequiredCount,
            List<String> supportedSports,Instant lastSchedulerTickAt,Instant nextSyncAt,Instant lastRunCompletedAt,
            int receivedCount,int insertedCount,int updatedCount,int skippedCount,int failedCount,Long durationMs,Integer lastHttpStatus,String lastErrorReason) { }
    private static final class Run {
        boolean attempted; Instant startedAt; int received;
        final Set<String> inserted=new HashSet<>(),updated=new HashSet<>(),skipped=new HashSet<>(),failed=new HashSet<>();
    }
    private String safeMessage(SportsProviderException ex) {
        return switch(ex.getReason()) {
            case NOT_CONFIGURED -> "Token do provedor não configurado.";
            case AUTHENTICATION -> "O provedor recusou a credencial ou o recurso não está incluído no plano.";
            case RATE_LIMITED -> "Quota protegida; novas consultas aguardam a janela permitida pelo provedor.";
            case TIMEOUT -> "Tempo limite ao consultar o provedor. Últimos dados salvos preservados.";
            default -> "Provedor temporariamente indisponível. Últimos dados salvos preservados.";
        };
    }
}

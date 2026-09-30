package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.SportsSyncStateStore.Feed;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Coordinates bounded feeds outside transactions; each persisted match is independently atomic. */
@Service
public class SportsSyncService {
    private static final Logger log=LoggerFactory.getLogger(SportsSyncService.class);
    private final SportsDataProvider provider;
    private final SportsSyncProperties properties;
    private final SportsSyncStateStore state;
    private final SportsCatalogSyncService catalog;
    private final SportsMatchSyncService matches;
    private final ArenaEventRepository events;
    public SportsSyncService(List<SportsDataProvider> providers,SportsSyncProperties properties,
            SportsSyncStateStore state,SportsCatalogSyncService catalog,SportsMatchSyncService matches,ArenaEventRepository events) {
        this.properties=properties; this.state=state; this.catalog=catalog; this.matches=matches; this.events=events;
        this.provider=providers.stream().filter(p -> !p.demo() && p.providerId().equalsIgnoreCase(properties.provider()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown sports.sync.provider"));
    }
    public void synchronize() {
        if (!properties.enabled() || !provider.available()) return;
        Instant now=Instant.now();
        if (provider.nextAllowedRequestAt()!=null && now.isBefore(provider.nextAllowedRequestAt())) return;
        String owner=UUID.randomUUID().toString(), id=provider.providerId();
        if (!state.claim(id,owner,now,now.plusMillis(properties.leaseMs()))) return;
        try {
            log.debug("[SPORTS_SYNC] Starting synchronization provider={}",id);
            boolean attempted=false;
            attempted|=feed(Feed.RUNNING,properties.runningIntervalMs(),now,provider::runningMatches);
            attempted|=feed(Feed.TRACKED,properties.trackedIntervalMs(),now,() -> {
                Instant oldest=now.minus(properties.correctionWindowHours(),ChronoUnit.HOURS);
                matches.quarantineExpiredResults(id,oldest,properties.trackedBatchSize());
                List<String> ids=events.findTrackedExternalIds(id,now.plus(properties.nearStartMinutes(),ChronoUnit.MINUTES),
                        oldest,EventStatus.FINISHED,
                        List.of(EventStatus.FINISHED,EventStatus.CANCELLED),PageRequest.of(0,properties.trackedBatchSize()));
                // null denotes no HTTP request, so it must not advance lastSuccessAt.
                return ids.isEmpty()?null:provider.matchDetails(ids);
            });
            attempted|=feed(Feed.UPCOMING,properties.upcomingIntervalMs(),now,
                    () -> provider.upcomingMatches(now,now.plus(properties.upcomingDays(),ChronoUnit.DAYS)));
            attempted|=feed(Feed.FINISHED,properties.finishedIntervalMs(),now,
                    () -> provider.finishedMatches(now.minus(properties.correctionWindowHours(),ChronoUnit.HOURS)));
            if (attempted) state.status(id,"ONLINE","Sincronização concluída; dados persistidos disponíveis.");
        } catch (SportsProviderException ex) {
            String status=ex.getReason()==SportsProviderException.Reason.RATE_LIMITED?"RATE_LIMITED":"UNAVAILABLE";
            state.status(id,status,safeMessage(ex));
            log.warn("[SPORTS_SYNC] Provider unavailable provider={} reason={} retryAt={}; keeping persisted data",id,ex.getReason(),ex.getRetryAt());
        } catch (RuntimeException ex) {
            state.status(id,"UNAVAILABLE","Falha ao sincronizar; os últimos dados salvos continuam disponíveis.");
            // Do not log remote exception messages or SQL values containing external content.
            log.error("[SPORTS_SYNC] Synchronization failed provider={} errorType={}",id,ex.getClass().getSimpleName());
        } finally { state.release(id,owner); }
    }
    private boolean feed(Feed feed,long interval,Instant now,Supplier<List<SportsMatch>> fetch) {
        Instant last=state.lastFeed(provider.providerId(),feed);
        if (last!=null && now.isBefore(last.plusMillis(interval))) return false;
        List<SportsMatch> snapshots=fetch.get();
        if (snapshots==null) {
            state.checkedWithoutRequest(provider.providerId(),feed,now);
            return false;
        }
        log.info("[SPORTS_SYNC] {} matches received: {}",feed,snapshots.size());
        var refs=catalog.synchronize(provider.providerId(),snapshots);
        boolean failed=false;
        for (SportsMatch snapshot:snapshots) {
            try { matches.synchronize(provider.providerId(),snapshot,refs); }
            catch (RuntimeException ex) {
                failed=true;
                log.error("[SPORTS_SYNC] Match transaction rolled back externalId={} errorType={}",snapshot.externalId(),ex.getClass().getSimpleName());
            }
        }
        if (failed) throw new IllegalStateException("One or more sports match transactions failed");
        state.completed(provider.providerId(),feed,now);
        return true;
    }
    public Status status() {
        var saved=state.snapshot(provider.providerId());
        String status=!properties.enabled()?"DISABLED":!provider.available()?"UNCONFIGURED":saved.status();
        String message=!properties.enabled()?"Sincronização externa desativada.":!provider.available()?
                "PANDASCORE_API_TOKEN não configurado. Os dados salvos e o modo Demo continuam disponíveis.":saved.message();
        if ("ONLINE".equals(status) && provider.nextAllowedRequestAt()!=null) {
            status="RATE_LIMITED";
            message="Quota protegida; novas consultas aguardam a janela permitida pelo provedor.";
        }
        return new Status(provider.providerName(),properties.enabled(),provider.available(),status,saved.lastAttemptAt(),
                saved.lastSuccessAt(),provider.nextAllowedRequestAt(),provider.remainingRequests(),message,
                events.countByExternalProviderAndResultReviewRequiredTrue(provider.providerId()));
    }
    public record Status(String provider,boolean enabled,boolean configured,String status,Instant lastAttemptAt,
            Instant lastSuccessAt,Instant nextAllowedRequestAt,Long requestsRemaining,String message,long reviewRequiredCount) { }
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

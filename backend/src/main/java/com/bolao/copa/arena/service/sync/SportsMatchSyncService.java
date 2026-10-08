package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.SportsMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One transaction per match: snapshot, settlement and processing marker commit together. */
@Service
public class SportsMatchSyncService {
    private static final Logger log=LoggerFactory.getLogger(SportsMatchSyncService.class);
    private final ArenaEventRepository events;
    private final CompetitorRepository teams;
    private final ChampionshipRepository championships;
    private final PredictionMarketRepository markets;
    private final MarketTemplateService templates;
    private final ArenaPredictionService predictions;
    private final MarketAvailabilityService availability;
    private final SportsSyncStateStore state;
    private final AdminAuditService audit;
    private final ObjectMapper json;
    private final EventParticipantRepository participants;
    private final LiveTrainingService training;

    public SportsMatchSyncService(ArenaEventRepository events, CompetitorRepository teams,
            ChampionshipRepository championships, PredictionMarketRepository markets, MarketTemplateService templates,
            ArenaPredictionService predictions, MarketAvailabilityService availability, SportsSyncStateStore state,
            AdminAuditService audit, ObjectMapper json, EventParticipantRepository participants, LiveTrainingService training) {
        this.events=events; this.teams=teams; this.championships=championships; this.markets=markets;
        this.templates=templates; this.predictions=predictions; this.availability=availability;
        this.state=state; this.audit=audit; this.json=json;
        this.participants=participants;
        this.training=training;
    }

    @Transactional
    public boolean synchronize(String provider, SportsMatch source, SportsCatalogSyncService.References refs) {
        if (!SportsCatalogSyncService.text(source.externalId()) || source.status()==null) return false;
        state.lock(provider);
        ArenaEvent event=events.findByExternalProviderAndExternalId(provider,source.externalId()).orElse(null);
        boolean creating=event==null;
        if (creating) {
            if (!completeIdentity(source,refs)) {
                log.info("[SPORTS_SYNC] Match deferred externalId={} reason=INCOMPLETE_SCHEDULE_OR_OPPONENTS",source.externalId());
                return false;
            }
            event=new ArenaEvent(); event.setExternalProvider(provider); event.setExternalId(source.externalId());
            event.setExternalKey(provider+":"+source.externalId()); event.setDemo(false);
            event.setChampionship(championships.getReferenceById(refs.championships().get(source.championship().externalId())));
            if(source.homeTeam()!=null) event.setHomeCompetitor(teams.getReferenceById(refs.teams().get(source.homeTeam().externalId())));
            if(source.awayTeam()!=null) event.setAwayCompetitor(teams.getReferenceById(refs.teams().get(source.awayTeam().externalId())));
            event.setStartsAt(source.scheduledAt()); event.setPredictionClosesAt(source.scheduledAt());
            event.setBestOf(source.bestOf());
        }
        Instant now=Instant.now();
        EventDataOwnership.requireCompatibleCatalog(event);
        event.setLastSyncedAt(now);
        SportsMatch match=orient(source,event);
        String snapshotHash=snapshotHash(match);
        if(!creating && snapshotHash.equals(event.getSourceSnapshotHash())) {
            // A new contract version must reconcile existing snapshots too; no provider refetch or event fabrication.
            if(EsportsMarketFactory.supports(event) && !event.isResultReviewRequired()
                    && (event.getStatus()==EventStatus.LIVE || event.getStatus()==EventStatus.SCHEDULED)) {
                int previous=markets.findByEventOrderByIdAsc(event).size();
                templates.generate(event.getId());
                var current=markets.findByEventForUpdate(event); availability.closeDeterminedMarkets(current);
                return current.size()>previous;
            }
            return false;
        }
        if (!sameParticipants(match,event)) {
            review(event,source,"PARTICIPANTS_CHANGED"); return true;
        }
        if (!creating && match.championship()!=null && SportsCatalogSyncService.text(match.championship().externalId())
                && !Objects.equals(match.championship().externalId(),event.getChampionship().getExternalId())) {
            // Championship membership can affect pool eligibility; retain the published identity for review.
            review(event,match,"CHAMPIONSHIP_CHANGED"); return true;
        }
        if (event.getResultProcessedAt()!=null) {
            if (match.status()==EventStatus.CANCELLED || (completeResult(match) && !fingerprint(match).equals(event.getResultFingerprint())))
                review(event,match,"OFFICIAL_RESULT_CORRECTION");
            return false;
        }
        if (event.getStatus()==EventStatus.CANCELLED) return true; // Refunds cannot be silently reversed.
        if (event.getStatus()==EventStatus.FINISHED
                && (match.status()==EventStatus.CANCELLED || match.status()==EventStatus.POSTPONED)) {
            review(event,match,"OFFICIAL_STATUS_CORRECTION"); return true;
        }
        if (!creating && event.getBestOf()!=null && match.bestOf()!=null && !event.getBestOf().equals(match.bestOf())
                && !markets.findByEventOrderByIdAsc(event).isEmpty()) {
            review(event,match,"SERIES_FORMAT_CHANGED"); return true;
        }
        if (event.isResultReviewRequired()) return true;
        if (!creating && !transitionAllowed(event.getStatus(),match.status())) {
            log.debug("[SPORTS_SYNC] Ignored stale status externalId={} current={} incoming={}",match.externalId(),event.getStatus(),match.status());
            return true;
        }
        if (match.scheduledAt()!=null && event.getStatus()!=EventStatus.LIVE && event.getStatus()!=EventStatus.FINISHED) {
            event.setStartsAt(match.scheduledAt()); event.setPredictionClosesAt(match.scheduledAt());
        }
        event.setTitle(SportsCatalogSyncService.cut(SportsCatalogSyncService.text(match.title())?match.title():
                match.championship().name(),180));
        if (match.championship()!=null) event.setStage(SportsCatalogSyncService.cut(match.championship().name(),100));
        if (match.bestOf()!=null) event.setBestOf(match.bestOf());
        event.setFormat(match.formatHint()==SportsMatch.EventFormatHint.RACE?EventFormat.RACE:format(event.getBestOf()));
        event.setSourceStatus(SportsCatalogSyncService.cut(match.rawStatus(),40));
        event.setSourceMetrics(String.join(",",new TreeSet<>(match.supportedMetrics())));
        event.setSourceSnapshotHash(snapshotHash); event.setDataQuality("SNAPSHOT");
        event.setClock(SportsCatalogSyncService.cut(match.clock(),80)); event.setPeriod(SportsCatalogSyncService.cut(match.period(),80));
        try { event.setResultData(json.writeValueAsString(match.resultData())); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        events.saveAndFlush(event);
        syncParticipants(event,match,refs);
        if (match.status()==EventStatus.CANCELLED) {
            predictions.cancelEvent(event.getId());
            training.settleOfficial(event);
            log.info("[SPORTS_SYNC] Match cancelled externalId={} refunds processed",match.externalId());
            return true;
        }
        event.setStatus(match.status());
        event.setLiveScoreAvailable(match.status()==EventStatus.LIVE && match.liveScoreAvailable()
                && match.homeScore()!=null && match.awayScore()!=null);
        if (match.status()==EventStatus.FINISHED || event.isLiveScoreAvailable()) {
            event.setHomeScore(match.homeScore()); event.setAwayScore(match.awayScore());
        } else { event.setHomeScore(null); event.setAwayScore(null); }
        event.setWinnerExternalId(match.winnerExternalId());
        if (match.endedAt()!=null) event.setFinishedAt(match.endedAt());
        if (match.status()==EventStatus.SCHEDULED || (match.status()==EventStatus.LIVE && EsportsMarketFactory.supports(event))) templates.generate(event.getId());
        var eventMarkets=markets.findByEventForUpdate(event);
        if(match.status()==EventStatus.POSTPONED) eventMarkets.stream().filter(m->m.getStatus()==MarketStatus.OPEN)
                .forEach(m->{m.setStatus(MarketStatus.SUSPENDED);m.setStatusReason("Evento adiado pelo provedor.");});
        Instant startsAt=event.getStartsAt(), closesAt=event.getPredictionClosesAt();
        // Rescheduling updates the original pre-match windows, but never reopens a closed market.
        eventMarkets.stream().filter(m -> m.getStatus()==MarketStatus.OPEN && m.getTimingMode()==MarketTimingMode.PRE_MATCH_ONLY)
                .forEach(m -> { m.setOpensAt(startsAt.minusSeconds(7*86400L)); m.setClosesAt(closesAt); });
        availability.closeDeterminedMarkets(eventMarkets);
        if (match.status()==EventStatus.FINISHED && completeResult(match) && validFinalScore(match)) {
            log.info("[PREDICTION] Processing provider result matchId={} score={}-{}",event.getId(),match.homeScore(),match.awayScore());
            if(match.formatHint()!=SportsMatch.EventFormatHint.RACE || !eventMarkets.isEmpty()) predictions.settleDerived(event);
            event.setResultProcessedAt(now); event.setResultFingerprint(fingerprint(match));
            training.settleOfficial(event);
            audit.record("EXTERNAL_RESULT_PROCESSED","EVENT",event.getId(),"Resultado recebido de "+provider+"; palpites processados pelas regras existentes.");
            log.info("[RANKING] Result processed matchId={}; ranking projections updated",event.getId());
        } else if (match.status()==EventStatus.FINISHED && match.formatHint()!=SportsMatch.EventFormatHint.RACE
                && (match.forfeit() || (match.draw()&&!"FOOTBALL".equals(match.sportCode())))) {
            review(event,match,"NON_STANDARD_RESULT");
        }
        log.info("[SPORTS_SYNC] Match {} externalId={} status={}",creating?"created":"updated",match.externalId(),event.getStatus());
        return true;
    }

    /** Stop spending provider quota on unresolved old results and expose every expiry in the audit trail. */
    @Transactional
    public int quarantineExpiredResults(String provider, Instant oldest, int batchSize) {
        state.lock(provider);
        List<String> expired=events.findExpiredIncompleteExternalIds(provider,oldest,EventStatus.FINISHED,
                PageRequest.of(0,batchSize));
        int reviewed=0;
        for (String externalId:expired) {
            ArenaEvent event=events.findByExternalProviderAndExternalId(provider,externalId).orElseThrow();
            Instant reference=event.getFinishedAt()!=null?event.getFinishedAt():event.getStartsAt();
            if (event.isResultReviewRequired() || event.getResultProcessedAt()!=null
                    || event.getStatus()!=EventStatus.FINISHED || !reference.isBefore(oldest)) continue;
            Map<String,Object> candidate=new LinkedHashMap<>();
            candidate.put("reason","INCOMPLETE_RESULT_EXPIRED"); candidate.put("status",event.getStatus());
            candidate.put("homeScore",event.getHomeScore()); candidate.put("awayScore",event.getAwayScore());
            candidate.put("winnerExternalId",event.getWinnerExternalId()); candidate.put("bestOf",event.getBestOf());
            candidate.put("resultReferenceAt",reference.toString());
            recordReview(event,candidate,"INCOMPLETE_RESULT_EXPIRED");
            reviewed++;
        }
        return reviewed;
    }

    private boolean completeIdentity(SportsMatch match,SportsCatalogSyncService.References refs) {
        if(match.formatHint()==SportsMatch.EventFormatHint.RACE)
            return match.scheduledAt()!=null&&match.championship()!=null&&refs.championships().containsKey(match.championship().externalId());
        return match.scheduledAt()!=null && match.homeTeam()!=null && match.awayTeam()!=null && match.championship()!=null
                && refs.teams().containsKey(match.homeTeam().externalId()) && refs.teams().containsKey(match.awayTeam().externalId())
                && !match.homeTeam().externalId().equals(match.awayTeam().externalId())
                && refs.championships().containsKey(match.championship().externalId());
    }
    private boolean sameParticipants(SportsMatch match,ArenaEvent event) {
        if(match.formatHint()==SportsMatch.EventFormatHint.RACE) return true;
        return (match.homeTeam()==null || Objects.equals(match.homeTeam().externalId(),event.getHomeCompetitor().getExternalId()))
                && (match.awayTeam()==null || Objects.equals(match.awayTeam().externalId(),event.getAwayCompetitor().getExternalId()));
    }
    private SportsMatch orient(SportsMatch match,ArenaEvent event) {
        if (match.homeTeam()!=null && match.awayTeam()!=null
                && Objects.equals(match.homeTeam().externalId(),event.getAwayCompetitor().getExternalId())
                && Objects.equals(match.awayTeam().externalId(),event.getHomeCompetitor().getExternalId()))
            return new SportsMatch(match.externalId(),match.title(),match.awayTeam(),match.homeTeam(),match.championship(),
                    match.scheduledAt(),match.endedAt(),match.status(),match.awayScore(),match.homeScore(),match.bestOf(),
                    match.winnerExternalId(),match.forfeit(),match.draw(),match.liveScoreAvailable(),match.sportCode(),
                    swapData(match.resultData()),match.participants(),match.rawStatus(),match.clock(),match.period(),match.supportedMetrics(),match.formatHint());
        return match;
    }
    private boolean completeResult(SportsMatch match) {
        if(match.formatHint()==SportsMatch.EventFormatHint.RACE) return match.status()==EventStatus.FINISHED&&match.winnerExternalId()!=null&&!match.participants().isEmpty();
        if(List.of("FOOTBALL","BASKETBALL","TENNIS").contains(match.sportCode()))
            return match.status()==EventStatus.FINISHED&&match.homeScore()!=null&&match.awayScore()!=null
                    &&match.homeTeam()!=null&&match.awayTeam()!=null;
        return match.status()==EventStatus.FINISHED && match.homeScore()!=null && match.awayScore()!=null
                && match.bestOf()!=null && match.winnerExternalId()!=null && match.homeTeam()!=null && match.awayTeam()!=null;
    }
    private boolean validFinalScore(SportsMatch match) {
        if(match.formatHint()==SportsMatch.EventFormatHint.RACE) return match.participants().stream().filter(p->Integer.valueOf(1).equals(p.position())).count()==1;
        if("FOOTBALL".equals(match.sportCode())) return !match.forfeit()&&match.homeScore()>=0&&match.awayScore()>=0;
        if("BASKETBALL".equals(match.sportCode())) return !match.forfeit()&&match.homeScore()>=0&&match.awayScore()>=0&&!match.homeScore().equals(match.awayScore());
        if("TENNIS".equals(match.sportCode())) return !match.forfeit()&&match.winnerExternalId()!=null
                &&match.winnerExternalId().equals(match.homeScore()>match.awayScore()?match.homeTeam().externalId():match.awayTeam().externalId())
                &&Set.of(2,3).contains(Math.max(match.homeScore(),match.awayScore()))&&Math.min(match.homeScore(),match.awayScore())<Math.max(match.homeScore(),match.awayScore());
        if (match.forfeit() || match.draw() || match.homeScore()<0 || match.awayScore()<0) return false;
        int target=match.bestOf()/2+1;
        return Math.max(match.homeScore(),match.awayScore())==target && Math.min(match.homeScore(),match.awayScore())<target
                && match.winnerExternalId().equals(match.homeScore()>match.awayScore()?match.homeTeam().externalId():match.awayTeam().externalId());
    }
    private boolean transitionAllowed(EventStatus current,EventStatus target) {
        if (current==target) return true;
        return switch(current) {
            case SCHEDULED, OPEN_FOR_PREDICTIONS -> true; // A complete game may occur between polls.
            case LIVE -> target==EventStatus.FINISHED || target==EventStatus.CANCELLED || target==EventStatus.POSTPONED;
            case POSTPONED -> target==EventStatus.SCHEDULED || target==EventStatus.LIVE || target==EventStatus.FINISHED || target==EventStatus.CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
    }
    private EventFormat format(Integer bestOf) {
        if (bestOf==null) return EventFormat.STANDARD;
        return switch(bestOf) { case 1 -> EventFormat.BO1; case 3 -> EventFormat.BO3; case 5 -> EventFormat.BO5; default -> EventFormat.STANDARD; };
    }
    private String fingerprint(SportsMatch match) {
        String value=match.status()+":"+match.homeScore()+":"+match.awayScore()+":"+match.bestOf()+":"+match.winnerExternalId()+":"+match.forfeit()+":"+match.draw();
        if(!match.resultData().isEmpty()) value+=":"+new TreeMap<>(match.resultData());
        if(!match.participants().isEmpty()) value+=":"+match.participants().stream().sorted(Comparator.comparing(p->p.participant().externalId()))
                .map(p->p.participant().externalId()+":"+p.position()+":"+p.scoreLabel()).toList();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void review(ArenaEvent event,SportsMatch match,String reason) {
        Map<String,Object> candidate=new LinkedHashMap<>();
        candidate.put("reason",reason); candidate.put("status",match.status()); candidate.put("homeScore",match.homeScore());
        candidate.put("awayScore",match.awayScore()); candidate.put("winnerExternalId",match.winnerExternalId());
        candidate.put("bestOf",match.bestOf()); candidate.put("forfeit",match.forfeit()); candidate.put("draw",match.draw());
        candidate.put("homeExternalId",match.homeTeam()==null?null:match.homeTeam().externalId());
        candidate.put("awayExternalId",match.awayTeam()==null?null:match.awayTeam().externalId());
        candidate.put("championshipExternalId",match.championship()==null?null:match.championship().externalId());
        candidate.put("championshipName",match.championship()==null?null:match.championship().name());
        candidate.put("resultData",new TreeMap<>(match.resultData()));
        if(!match.participants().isEmpty()) candidate.put("classification",match.participants().stream().map(p->{
            Map<String,Object> value=new LinkedHashMap<>();value.put("externalId",p.participant().externalId());value.put("position",p.position());value.put("scoreLabel",p.scoreLabel());return value;
        }).toList());
        recordReview(event,candidate,reason);
    }
    private String snapshotHash(SportsMatch match) {
        try {
            // Canonical ordering, excluding transport timestamps. Identical snapshots are genuine NO-OPs.
            String value=match.externalId()+"|"+match.sportCode()+"|"+match.title()+"|"+match.scheduledAt()+"|"+match.endedAt()+"|"+
                    match.status()+"|"+match.homeTeam()+"|"+match.awayTeam()+"|"+match.championship()+"|"+match.homeScore()+"|"+match.awayScore()+"|"+
                    match.bestOf()+"|"+match.winnerExternalId()+"|"+match.forfeit()+"|"+match.draw()+"|"+match.liveScoreAvailable()+"|"+
                    new TreeMap<>(match.resultData())+"|"+match.participants()+"|"+match.rawStatus()+"|"+match.clock()+"|"+match.period()+"|"+new TreeSet<>(match.supportedMetrics());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private Map<String,String> swapData(Map<String,String> data) {
        Map<String,String> result=new TreeMap<>();
        data.forEach((key,value)->result.put(key.endsWith("Home")?key.substring(0,key.length()-4)+"Away":
                key.endsWith("Away")?key.substring(0,key.length()-4)+"Home":key,value));return result;
    }
    private void syncParticipants(ArenaEvent event,SportsMatch match,SportsCatalogSyncService.References refs) {
        if(match.formatHint()!=SportsMatch.EventFormatHint.RACE) return;
        var known=participants.findByEventOrderByDisplayOrderAsc(event);
        int order=0;
        for(var source:match.participants()) {
            var entry=known.stream().filter(p->p.getCompetitor().getExternalId().equals(source.participant().externalId())).findFirst().orElseGet(EventParticipant::new);
            entry.setEvent(event);entry.setCompetitor(teams.getReferenceById(refs.teams().get(source.participant().externalId())));
            entry.setDisplayOrder(++order);entry.setPosition(source.position());entry.setScoreLabel(SportsCatalogSyncService.cut(source.scoreLabel(),80));
            participants.save(entry);
        }
    }
    private void recordReview(ArenaEvent event,Map<String,Object> candidate,String reason) {
        try {
            String pending=json.writeValueAsString(candidate);
            if (!pending.equals(event.getPendingResultData())) {
                event.setPendingResultData(pending); event.setResultReviewRequired(true);
                markets.findByEventForUpdate(event).stream().filter(m -> m.getStatus()==MarketStatus.OPEN)
                        .forEach(m -> { m.setStatus(MarketStatus.SUSPENDED); m.setStatusReason("Dados do provedor sob revisão administrativa."); });
                audit.record("EXTERNAL_RESULT_REVIEW_REQUIRED","EVENT",event.getId(),"Atualização externa requer revisão: "+reason);
                log.warn("[SPORTS_SYNC] Result review required matchId={} reason={}",event.getId(),reason);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
}

package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.security.JwtAuthenticationFilter.TrainingIdentity;
import java.math.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Training owns its rows; sports entities are locked/read but never mutated here. */
@Service
public class LiveTrainingService {
    private final JdbcTemplate db;
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final PredictionSelectionRules rules;
    private final MarketDefinitionCatalog definitions;
    private final MarketSettlementEngine engine;
    private final EventParticipantRepository participants;
    private final PricingSnapshotCodec pricingSnapshots;
    public LiveTrainingService(JdbcTemplate db, ArenaEventRepository events, PredictionMarketRepository markets,
            MarketOptionRepository options, PredictionSelectionRules rules, MarketDefinitionCatalog definitions,
            MarketSettlementEngine engine, EventParticipantRepository participants,PricingSnapshotCodec pricingSnapshots) {
        this.db=db;this.events=events;this.markets=markets;this.options=options;this.rules=rules;
        this.definitions=definitions;this.engine=engine;this.participants=participants;
        this.pricingSnapshots=pricingSnapshots;
    }
    @Transactional
    public String createSession() {
        String id=UUID.randomUUID().toString(); Instant now=Instant.now();
        // Serialize capacity checks across application instances, with bounded public storage growth.
        db.queryForMap("select id from demo_training_capacity where id=1 for update");
        if(db.queryForObject("select count(*) from demo_training_sessions where created_at>?",Long.class,stamp(now.minusSeconds(60)))>=30
                || db.queryForObject("select count(*) from demo_training_sessions where expires_at>?",Long.class,stamp(now))>=1000
                || db.queryForObject("select count(*) from demo_training_sessions",Long.class)>=100000)
            throw new TrainingCapacityException();
        db.update("insert into demo_training_sessions(id,balance,created_at,updated_at,expires_at) values (?,?,?,?,?)",
                id,PointWalletService.INITIAL_VIRTUAL_POINTS,stamp(now),stamp(now),stamp(now.plusSeconds(86400)));
        ledger(id,"INITIAL_BONUS",PointWalletService.INITIAL_VIRTUAL_POINTS,PointWalletService.INITIAL_VIRTUAL_POINTS,
                "initial","Saldo inicial do treino — pontos exclusivamente virtuais",null);
        return id;
    }
    public static final class TrainingCapacityException extends RuntimeException {
        public TrainingCapacityException() { super("O treino atingiu o limite temporário de sessões. Tente novamente mais tarde."); }
    }
    private String currentSession() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getDetails() instanceof TrainingIdentity identity)) throw new AccessDeniedException("Entre no modo de treino Demo.");
        String id=identity.sessionId();
        if(db.queryForObject("select count(*) from demo_training_sessions where id=? and expires_at>?",Long.class,id,stamp(Instant.now()))!=1)
            throw new AccessDeniedException("Este treino expirou. Inicie uma nova sessão Demo.");
        return id;
    }
    @Transactional
    public PredictionResponse place(PlacePredictionRequest request,String headerKey) {
        String session=currentSession();
        if(request.poolId()!=null) throw new ArenaProblem.RuleViolation("O treino possui histórico e pontuação próprios, sem bolão real.");
        String key=headerKey!=null?headerKey:request.idempotencyKey();
        if(key==null||key.isBlank()) throw new ArenaProblem.RuleViolation("Informe a chave de confirmação do palpite.");
        key=key.trim();
        if(key.length()>MAX_CLIENT_IDEMPOTENCY_KEY_LENGTH) throw new ArenaProblem.RuleViolation("Chave de confirmação muito longa.");
        var event=events.findByIdForUpdate(request.eventId()).orElseThrow(()->new ArenaProblem.NotFound("Evento não encontrado."));
        var market=markets.findByIdForUpdate(request.marketId()).orElseThrow(()->new ArenaProblem.NotFound("Mercado não encontrado."));
        lock(session);
        var duplicate=db.queryForList("select * from demo_training_predictions where session_id=? and idempotency_key=?",session,key);
        if(!duplicate.isEmpty()) {
            var row=duplicate.getFirst();
            if(number(row,"event_id")!=request.eventId() || number(row,"market_id")!=request.marketId()
                    || number(row,"option_id")!=request.optionId() || number(row,"stake_points")!=request.stakePoints())
                throw new ArenaProblem.Conflict("A chave de confirmação já foi usada com dados diferentes.");
            return response(row);
        }
        if(event.isDemo() || !EsportsMarketFactory.supports(event) || event.getStatus()!=EventStatus.LIVE
                || market.getTimingMode()!=MarketTimingMode.LIVE_ONLY)
            throw new ArenaProblem.RuleViolation("O treino utiliza somente mercados ao vivo de partidas reais compatíveis.");
        if(!market.getEvent().getId().equals(event.getId())) throw new ArenaProblem.RuleViolation("O mercado não pertence ao evento informado.");
        var option=options.findByIdAndMarket(request.optionId(),market).orElseThrow(()->new ArenaProblem.NotFound("Opção não encontrada."));
        var quote=rules.confirm(market,option,request); BigDecimal multiplier=quote.multipliers().get(option.getKey());
        long balance=balance(session);
        if(balance<request.stakePoints()) throw new ArenaProblem.RuleViolation("Saldo do treino insuficiente.");
        if(db.queryForObject("select count(*) from demo_training_predictions where session_id=?",Long.class,session)>=100)
            throw new ArenaProblem.RuleViolation("Este treino atingiu o limite de 100 palpites. Inicie uma nova sessão Demo.");
        var values=new HashMap<String,Object>();
        values.put("session_id",session); values.put("event_id",event.getId()); values.put("market_id",market.getId()); values.put("option_id",option.getId());
        values.put("stake_points",request.stakePoints());values.put("multiplier",multiplier);values.put("model_version",quote.modelVersion());
        values.put("pricing_snapshot",pricingSnapshots.encode(quote.assessment()));
        values.put("potential_points",multiplier.multiply(BigDecimal.valueOf(request.stakePoints())).setScale(0,RoundingMode.DOWN).intValueExact());
        values.put("rewarded_points",0);values.put("status","ACTIVE");values.put("idempotency_key",key);values.put("placed_at",stamp(Instant.now()));
        long id=new SimpleJdbcInsert(db).withTableName("demo_training_predictions").usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
        db.update("update demo_training_sessions set balance=balance-?, lifetime_used=lifetime_used+?, updated_at=? where id=?",
                request.stakePoints(),request.stakePoints(),stamp(Instant.now()),session);
        ledger(session,"PREDICTION_PLACED",-request.stakePoints(),balance-request.stakePoints(),"place:"+id,"Palpite de demonstração — treino ao vivo",Long.toString(id));
        return response(db.queryForMap("select * from demo_training_predictions where id=? and session_id=?",id,session));
    }
    @Transactional(readOnly=true)
    public List<PredictionResponse> predictions() {
        return db.queryForList("select * from demo_training_predictions where session_id=? order by placed_at desc,id desc",currentSession()).stream().map(this::response).toList();
    }
    @Transactional(readOnly=true)
    public WalletResponse wallet() {
        var row=db.queryForMap("select * from demo_training_sessions where id=?",currentSession());
        return new WalletResponse(number(row,"balance"),number(row,"lifetime_earned"),number(row,"lifetime_used"),instant(row,"updated_at"),"Carteira exclusiva deste treino. "+VIRTUAL_POINTS_NOTICE);
    }
    @Transactional(readOnly=true)
    public com.bolao.copa.arena.api.ExperienceDtos.ProfileResponse profile() {
        var row=db.queryForMap("select * from demo_training_sessions where id=?",currentSession());
        return new com.bolao.copa.arena.api.ExperienceDtos.ProfileResponse(0L,"Jogador Demo — este treino","","PARTICIPANTE",instant(row,"created_at"),
                null,"Sessão de treino isolada. Para uma conta persistente, cadastre-se.",List.of(),"light","pt-BR",false,false,1,0,number(row,"balance"));
    }
    @Transactional(readOnly=true)
    public List<PointTransactionResponse> transactions() {
        return db.queryForList("select * from demo_training_ledger where session_id=? order by created_at desc,id desc",currentSession()).stream()
                .map(r->new PointTransactionResponse(number(r,"id"),PointTransactionType.valueOf((String)r.get("type")),number(r,"amount"),number(r,"balance_after"),
                        (String)r.get("description"),"TRAINING",(String)r.get("reference_id"),instant(r,"created_at"))).toList();
    }
    @Transactional(readOnly=true)
    public List<RankingRow> ranking(String sport) {
        var mine=predictions().stream().filter(p->sport==null||sport.isBlank()||sport.equalsIgnoreCase(p.sportCode())).toList();
        long resolved=mine.stream().filter(p->p.status()==PredictionStatus.WON||p.status()==PredictionStatus.LOST).count();
        long won=mine.stream().filter(p->p.status()==PredictionStatus.WON).count();
        long points=mine.stream().mapToLong(PredictionResponse::rewardedPoints).sum();
        var latest=mine.stream().filter(p->p.status()==PredictionStatus.WON||p.status()==PredictionStatus.LOST)
                .sorted(Comparator.comparing((PredictionResponse p)->p.resolvedAt()==null?p.placedAt():p.resolvedAt()).thenComparing(PredictionResponse::id).reversed()).toList();
        long streak=latest.stream().takeWhile(p->p.status()==PredictionStatus.WON).count();
        return List.of(new RankingRow(1,0L,"Jogador Demo — este treino",null,points,won,resolved,resolved==0?0:Math.round(won*10000.0/resolved)/100.0,streak,true));
    }
    @Transactional(readOnly=true)
    public int bestStreak() {
        int best=0,current=0;
        var ordered=predictions().stream().filter(p->p.status()==PredictionStatus.WON||p.status()==PredictionStatus.LOST)
                .sorted(Comparator.comparing((PredictionResponse p)->p.resolvedAt()==null?p.placedAt():p.resolvedAt()).thenComparing(PredictionResponse::id)).toList();
        for(var prediction:ordered) { current=prediction.status()==PredictionStatus.WON?current+1:0;best=Math.max(best,current); }
        return best;
    }
    /** Only the official sync invokes this after validation; never a client result command. */
    @Transactional
    public void settleOfficial(ArenaEvent event) {
        if(event.isDemo()||event.getExternalProvider()==null||event.isResultReviewRequired()) return;
        boolean cancelled=event.getStatus()==EventStatus.CANCELLED;
        if(!cancelled && (event.getStatus()!=EventStatus.FINISHED || event.getResultProcessedAt()==null)) return;
        var active=db.queryForList("select * from demo_training_predictions where event_id=? and status='ACTIVE' order by session_id,id",event.getId());
        active.stream().map(r->(String)r.get("session_id")).distinct().sorted().forEach(this::lock);
        var entries=participants.findByEventOrderByDisplayOrderAsc(event);
        for(var row:active) {
            var market=markets.findById(number(row,"market_id")).orElseThrow();
            var definition=definitions.definition(market,entries).orElseThrow();
            var outcome=cancelled?MarketSettlementEngine.Outcome.voided():engine.evaluate(definition,event,entries);
            var option=options.findById(number(row,"option_id")).orElseThrow();
            boolean won=outcome.winningKeys().contains(option.getKey());
            long credit=outcome.refund()?number(row,"stake_points"):won?number(row,"potential_points"):0;
            String status=outcome.refund()?"REFUNDED":won?"WON":"LOST";
            long id=number(row,"id");String session=(String)row.get("session_id");
            int changed=db.update("update demo_training_predictions set status=?,rewarded_points=?,resolved_at=? where id=? and status='ACTIVE'",
                    status,won?credit:0,stamp(Instant.now()),id);
            if(changed==1 && credit>0) {
                db.update("update demo_training_sessions set balance=balance+?,lifetime_earned=lifetime_earned+?,updated_at=? where id=?",
                        credit,won?credit:0,stamp(Instant.now()),session);
                ledger(session,won?"PREDICTION_WON":"REFUND",credit,balance(session),"settle:"+id,
                        won?"Recompensa do treino por resultado oficial":"Reembolso do treino por resultado oficial",Long.toString(id));
            }
        }
    }
    private PredictionResponse response(Map<String,Object> row) {
        var event=events.findById(number(row,"event_id")).orElseThrow();
        var market=markets.findById(number(row,"market_id")).orElseThrow();
        var option=options.findById(number(row,"option_id")).orElseThrow();
        return new PredictionResponse(number(row,"id"),event.getId(),event.getTitle(),market.getId(),market.getName(),option.getId(),option.getLabel(),
                (int)number(row,"stake_points"),(BigDecimal)row.get("multiplier"),(int)number(row,"potential_points"),(int)number(row,"rewarded_points"),
                PredictionStatus.valueOf((String)row.get("status")),null,instant(row,"placed_at"),instant(row,"resolved_at"),false,event.getStatus().name(),
                event.getChampionship().getSport().getCode(),event.getChampionship().getSport().getName(),true,"INTERNAL_MODEL",(String)row.get("model_version"));
    }
    private void lock(String session) { db.queryForMap("select id from demo_training_sessions where id=? for update",session); }
    private long balance(String session) { return db.queryForObject("select balance from demo_training_sessions where id=?",Long.class,session); }
    private void ledger(String session,String type,long amount,long balance,String key,String description,String reference) {
        db.update("insert into demo_training_ledger(session_id,type,amount,balance_after,idempotency_key,description,reference_id,created_at) values (?,?,?,?,?,?,?,?)",
                session,type,amount,balance,key,description,reference,stamp(Instant.now()));
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static Timestamp stamp(Instant time) { return Timestamp.from(time); }
    private static Instant instant(Map<String,Object> row,String key) {
        Object value=row.get(key);return value==null?null:value instanceof java.time.OffsetDateTime time?time.toInstant():((Timestamp)value).toInstant();
    }
}

import com.bolao.copa.CopaApplication;
import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.pandascore.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.support.RegularTestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Manual only. Replays two authentic snapshots in an isolated H2 database; never touches production users. */
public class RealPredictionFlowProbe {
    public static void main(String[] args) throws Exception {
        if(args.length<3 || args.length>4)throw new IllegalArgumentException("before.json after.json proof.json [--persist] required");
        Path beforeFile=Path.of(args[0]),afterFile=Path.of(args[1]),proofFile=Path.of(args[2]);
        boolean persistent=args.length==4&&"--persist".equals(args[3]);
        Path checkpoint=proofFile.resolveSibling(proofFile.getFileName()+".active.json");
        String db=persistent?"jdbc:h2:file:"+proofFile.toAbsolutePath().toString().replace('\\','/')+".qa-db":"jdbc:h2:mem:realpredictionprobe";
        try(var app=new SpringApplicationBuilder(CopaApplication.class).profiles("test").run(
                "--server.address=127.0.0.1","--server.port=0",
                "--spring.datasource.url="+db+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa","--spring.datasource.password=","--spring.datasource.driver-class-name=org.h2.Driver",
                "--sports.sync.enabled=false","--sports.sync.scheduler-enabled=false","--sports.pandascore.api-token=",
                "--app.demo.live-scheduler-enabled=false","--app.demo.schedule-initial-delay-ms=86400000","--app.demo.history-initial-delay-ms=86400000")) {
            var json=app.getBean(ObjectMapper.class);var mapper=app.getBean(PandaScoreMapper.class);
            var catalog=app.getBean(SportsCatalogSyncService.class);var sync=app.getBean(SportsMatchSyncService.class);
            var events=app.getBean(ArenaEventRepository.class);var api=app.getBean(ArenaCatalogService.class);
            var commands=app.getBean(ArenaPredictionService.class);var users=app.getBean(UserRepository.class);
            var wallets=app.getBean(PointWalletService.class);var predictions=app.getBean(ArenaPredictionRepository.class);
            var ledger=app.getBean(PointLedgerRepository.class);var rankings=app.getBean(ArenaPoolRankingService.class);
            var tx=new org.springframework.transaction.support.TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            var before=json.readValue(Files.readString(beforeFile),PandaScoreDtos.Match.class);
            if(!"running".equals(before.status()))throw new IllegalStateException("Authentic running snapshot required; do not invent status");
            var source=mapper.match(before,false).orElseThrow();
            sync.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
            long eventId=tx.execute(status->events.findByExternalProviderAndExternalId("PANDASCORE",source.externalId()).orElseThrow().getId());
            Map<String,Object> active=persistent&&Files.exists(checkpoint)?json.readValue(Files.readString(checkpoint),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}):new LinkedHashMap<>();
            if(!active.isEmpty()&&!source.externalId().equals(active.get("externalId")))throw new IllegalStateException("Checkpoint belongs to another real match");
            var event=api.eventResponse(eventId);
            long marketId,homeUserId,awayUserId,homePredictionId,awayPredictionId;
            Instant predictionRegisteredAt;
            if(active.isEmpty()) {
            var market=event.markets().stream().filter(m->m.templateCode().equals("SERIES_WINNER_LIVE")&&m.availability().allowed()).findFirst().orElseThrow();
            var homeUser=RegularTestUsers.freshParticipant(users);var awayUser=RegularTestUsers.freshParticipant(users);
            var home=market.options().stream().filter(o->o.key().equals("HOME")).findFirst().orElseThrow();
            var away=market.options().stream().filter(o->o.key().equals("AWAY")).findFirst().orElseThrow();
            var pHome=commands.place(new PlacePredictionRequest(eventId,market.id(),home.id(),25,null,"real-home"),"real-home",homeUser);
            var pAway=commands.place(new PlacePredictionRequest(eventId,market.id(),away.id(),25,null,"real-away"),"real-away",awayUser);
            predictionRegisteredAt=Instant.now();marketId=market.id();homeUserId=homeUser.getId();awayUserId=awayUser.getId();homePredictionId=pHome.id();awayPredictionId=pAway.id();
            active.put("externalId",source.externalId());active.put("marketId",marketId);active.put("homeUserId",homeUserId);active.put("awayUserId",awayUserId);active.put("homePredictionId",homePredictionId);active.put("awayPredictionId",awayPredictionId);active.put("registeredAt",predictionRegisteredAt.toString());
            if(persistent)Files.writeString(checkpoint,json.writerWithDefaultPrettyPrinter().writeValueAsString(active));
            System.out.println("REAL_FLOW predictions registered in isolated H2: externalId="+source.externalId()+" market="+market.id()+" home="+pHome.id()+" away="+pAway.id()+" at="+Instant.now());
            } else {
                marketId=((Number)active.get("marketId")).longValue();homeUserId=((Number)active.get("homeUserId")).longValue();awayUserId=((Number)active.get("awayUserId")).longValue();homePredictionId=((Number)active.get("homePredictionId")).longValue();awayPredictionId=((Number)active.get("awayPredictionId")).longValue();predictionRegisteredAt=Instant.parse((String)active.get("registeredAt"));
                System.out.println("REAL_FLOW resumed existing QA predictions externalId="+source.externalId()+" registeredAt="+predictionRegisteredAt);
            }
            var homeUser=users.findById(homeUserId).orElseThrow();var awayUser=users.findById(awayUserId).orElseThrow();
            var market=api.eventResponse(eventId).markets().stream().filter(m->m.id().equals(marketId)).findFirst().orElseThrow();
            long end=System.currentTimeMillis()+30*60_000L;
            while(!Files.exists(afterFile)&&System.currentTimeMillis()<end)Thread.sleep(15_000);
            if(!Files.exists(afterFile))throw new IllegalStateException("No authentic finished snapshot yet; full lifecycle remains pending");
            var after=json.readValue(Files.readString(afterFile),PandaScoreDtos.Match.class);
            if(!after.id().equals(before.id())||!"finished".equals(after.status()))throw new IllegalStateException("Same real match with authentic final result required");
            var result=mapper.match(after,false).orElseThrow();
            sync.synchronize("PANDASCORE",result,catalog.synchronize("PANDASCORE",List.of(result)));
            var finished=api.eventResponse(eventId);
            if(finished.resultProcessedAt()==null)throw new IllegalStateException("Official result incomplete or awaiting review; no fabricated settlement");
            long homeBalance=wallets.wallet(homeUser).balance(),awayBalance=wallets.wallet(awayUser).balance();
            for(int n=0;n<10;n++)sync.synchronize("PANDASCORE",result,catalog.synchronize("PANDASCORE",List.of(result)));
            if(homeBalance!=wallets.wallet(homeUser).balance()||awayBalance!=wallets.wallet(awayUser).balance())throw new IllegalStateException("Repeated result changed wallet balance");
            var homePrediction=predictions.findById(homePredictionId).orElseThrow();var awayPrediction=predictions.findById(awayPredictionId).orElseThrow();
            if(!Set.of(PredictionStatus.WON,PredictionStatus.LOST).equals(Set.of(homePrediction.getStatus(),awayPrediction.getStatus())))throw new IllegalStateException("Expected one winner and one loser");
            long winningId=homePrediction.getStatus()==PredictionStatus.WON?homePredictionId:awayPredictionId;
            long losingId=homePrediction.getStatus()==PredictionStatus.LOST?homePredictionId:awayPredictionId;
            var credit=ledger.findByIdempotencyKey("prediction-win:"+winningId).orElseThrow();
            if(ledger.findByIdempotencyKey("prediction-win:"+losingId).isPresent())throw new IllegalStateException("Loser received prediction reward");
            long credits=ledger.findAll().stream().filter(l->l.getIdempotencyKey().equals("prediction-win:"+winningId)).count();
            if(credits!=1)throw new IllegalStateException("Duplicate winner credit");
            Map<String,Object> proof=new LinkedHashMap<>();
            proof.put("environment","isolated H2; authentic PandaScore snapshots; no production predictions");
            proof.put("provider","PANDASCORE");proof.put("externalId",source.externalId());proof.put("title",source.title());proof.put("sport",source.sportCode());
            proof.put("before",before.status());proof.put("after",after.status());proof.put("bestOf",source.bestOf());
            proof.put("beforeCapturedAt",json.readTree(Files.readString(beforeFile)).path("_evidence").path("capturedAt").asText());
            proof.put("afterCapturedAt",json.readTree(Files.readString(afterFile)).path("_evidence").path("capturedAt").asText());
            proof.put("predictionRegisteredAt",predictionRegisteredAt);proof.put("providerEndedAt",after.endAt());
            proof.put("predictionBeforeOfficialEnd",after.endAt()!=null&&predictionRegisteredAt.isBefore(after.endAt()));
            proof.put("score",List.of(finished.homeScore(),finished.awayScore()));proof.put("marketId",market.id());proof.put("options",market.options());
            proof.put("homePrediction",Map.of("id",homePredictionId,"status",homePrediction.getStatus(),"multiplier",homePrediction.getMultiplier(),"reward",homePrediction.getRewardedPoints()));
            proof.put("awayPrediction",Map.of("id",awayPredictionId,"status",awayPrediction.getStatus(),"multiplier",awayPrediction.getMultiplier(),"reward",awayPrediction.getRewardedPoints()));
            proof.put("winnerCredit",credit.getAmount());proof.put("winnerCredits",credits);proof.put("resultRetries",10);proof.put("balancesUnchangedOnRetry",true);
            proof.put("homeRanking",rankings.globalRanking(homeUser).stream().filter(r->r.userId().equals(homeUser.getId())).toList());
            proof.put("awayRanking",rankings.globalRanking(awayUser).stream().filter(r->r.userId().equals(awayUser.getId())).toList());proof.put("processedAt",finished.resultProcessedAt());
            Files.writeString(proofFile,json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            System.out.println("REAL_FLOW SUCCESS externalId="+source.externalId()+" winnerCredit="+credit.getAmount()+" credits="+credits+" repeatedResults=10");
        }
    }
}

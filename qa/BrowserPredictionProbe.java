import com.bolao.copa.CopaApplication;
import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.provider.pandascore.*;
import com.bolao.copa.arena.service.sync.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Manual browser QA only: authentic snapshots, local H2, no provider credentials or production writes. */
public class BrowserPredictionProbe {
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("before.json after-gate.json proof.json required");
        Path beforeFile=Path.of(args[0]),afterFile=Path.of(args[1]),proofFile=Path.of(args[2]);
        try(var app=new SpringApplicationBuilder(CopaApplication.class).profiles("test").run(
                "--server.address=127.0.0.1","--server.port=8088","--app.cors.allowed-origins=http://localhost:5173",
                "--spring.datasource.url=jdbc:h2:mem:browserprediction;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa","--spring.datasource.password=","--spring.datasource.driver-class-name=org.h2.Driver",
                "--sports.sync.enabled=false","--sports.sync.scheduler-enabled=false","--sports.pandascore.api-token=",
                "--app.demo.live-scheduler-enabled=false","--app.demo.schedule-initial-delay-ms=86400000","--app.demo.history-initial-delay-ms=86400000")) {
            var json=app.getBean(ObjectMapper.class);var mapper=app.getBean(PandaScoreMapper.class);
            var catalog=app.getBean(SportsCatalogSyncService.class);var sync=app.getBean(SportsMatchSyncService.class);
            var events=app.getBean(ArenaEventRepository.class);var predictions=app.getBean(ArenaPredictionRepository.class);var ledger=app.getBean(PointLedgerRepository.class);
            var tx=new org.springframework.transaction.support.TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            var before=json.readValue(Files.readString(beforeFile),PandaScoreDtos.Match.class);
            if(!"running".equals(before.status()))throw new IllegalStateException("Authentic running snapshot required");
            var source=mapper.match(before,false).orElseThrow();sync.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
            long eventId=tx.execute(s->events.findByExternalProviderAndExternalId("PANDASCORE",source.externalId()).orElseThrow().getId());
            System.out.println("BROWSER_QA READY eventId="+eventId+" externalId="+source.externalId()+" localPort=8088; register through UI");
            long deadline=System.currentTimeMillis()+30*60_000L;
            while(!Files.exists(afterFile)&&System.currentTimeMillis()<deadline)Thread.sleep(1000);
            if(!Files.exists(afterFile))throw new IllegalStateException("Browser QA result gate not supplied");
            var after=json.readValue(Files.readString(afterFile),PandaScoreDtos.Match.class);
            if(!after.id().equals(before.id())||!"finished".equals(after.status()))throw new IllegalStateException("Same authenticated final snapshot required");
            var result=mapper.match(after,false).orElseThrow();for(int n=0;n<10;n++)sync.synchronize("PANDASCORE",result,catalog.synchronize("PANDASCORE",List.of(result)));
            tx.execute(s->{var event=events.findByExternalProviderAndExternalId("PANDASCORE",source.externalId()).orElseThrow();
                var rows=predictions.findByEventAndStatus(event,PredictionStatus.WON);
                if(rows.isEmpty())throw new IllegalStateException("No winning prediction registered through browser");
                var proof=new LinkedHashMap<String,Object>();proof.put("mode","isolated browser replay of authenticated snapshots; not a temporal live claim");proof.put("externalId",source.externalId());
                proof.put("predictions",rows.stream().map(p->Map.of("id",p.getId(),"stake",p.getStakePoints(),"multiplier",p.getMultiplier(),"reward",p.getRewardedPoints(),"status",p.getStatus())).toList());
                proof.put("credits",rows.stream().map(p->ledger.findByIdempotencyKey("prediction-win:"+p.getId()).orElseThrow().getAmount()).toList());proof.put("resultRetries",10);
                try{Files.writeString(proofFile,json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));}catch(java.io.IOException e){throw new RuntimeException(e);}return null;});
            System.out.println("BROWSER_QA RESULT_PROCESSED; inspect predictions, wallet, statistics and ranking in UI");
            Thread.sleep(10*60_000L);
        }
    }
}

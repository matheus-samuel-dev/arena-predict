package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only composition; shared Demo profile/progression never enters training totals. */
@Service
public class LiveTrainingDashboardService {
    private final LiveTrainingService training;
    private final ArenaCatalogService catalog;
    public LiveTrainingDashboardService(LiveTrainingService training,ArenaCatalogService catalog) { this.training=training;this.catalog=catalog; }
    @Transactional(readOnly=true)
    public DashboardResponse dashboard() {
        var predictions=training.predictions();var rank=training.ranking(null).getFirst();
        var live=catalog.liveEvents();
        var upcoming=catalog.listEvents(EventStatus.SCHEDULED,null,null).stream().limit(8).toList();
        long active=predictions.stream().filter(p->p.status()==PredictionStatus.ACTIVE).count();
        return new DashboardResponse("Jogador Demo — este treino",1,"Treino isolado",0,0,training.wallet().balance(),1,
                active,rank.totalPredictions(),rank.correctPredictions(),rank.accuracy(),(int)rank.streak(),training.bestStreak(),0,
                live.stream().limit(3).toList(),live,upcoming,predictions.stream().limit(6).toList(),List.of(),List.of(rank),
                VIRTUAL_POINTS_NOTICE,true,List.of(),List.of(),List.of());
    }
}

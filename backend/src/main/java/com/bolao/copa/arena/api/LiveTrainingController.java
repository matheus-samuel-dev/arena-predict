package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.service.LiveTrainingService;
import com.bolao.copa.arena.service.LiveTrainingDashboardService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/training")
public class LiveTrainingController {
    private final LiveTrainingService training;
    private final LiveTrainingDashboardService dashboards;
    public LiveTrainingController(LiveTrainingService training,LiveTrainingDashboardService dashboards) { this.training=training;this.dashboards=dashboards; }
    @GetMapping("/dashboard") public DashboardResponse dashboard() { return dashboards.dashboard(); }
    @PostMapping("/predictions") @ResponseStatus(HttpStatus.CREATED)
    public PredictionResponse place(@Valid @RequestBody PlacePredictionRequest request,@RequestHeader(value="Idempotency-Key",required=false) String key) { return training.place(request,key); }
    @GetMapping("/predictions") public List<PredictionResponse> predictions() { return training.predictions(); }
    @GetMapping("/wallet") public WalletResponse wallet() { return training.wallet(); }
    @GetMapping("/wallet/transactions") public List<PointTransactionResponse> transactions() { return training.transactions(); }
    @GetMapping("/rankings") public List<RankingRow> ranking(@RequestParam(required=false) String sport) { return training.ranking(sport); }
    @GetMapping("/notifications") public List<NotificationResponse> notifications() { training.wallet(); return List.of(); }
    @GetMapping("/profile") public ExperienceDtos.ProfileResponse profile() { return training.profile(); }
    @GetMapping("/achievements") public List<ExperienceDtos.AchievementResponse> achievements() { training.wallet(); return List.of(); }
    @GetMapping("/challenges") public List<ExperienceDtos.ChallengeResponse> challenges() { training.wallet(); return List.of(); }
}

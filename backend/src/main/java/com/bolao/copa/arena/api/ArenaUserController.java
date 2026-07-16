package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.ArenaEnums.RankingPeriod;
import com.bolao.copa.arena.domain.ArenaEnums.RankingScope;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ArenaUserController {
    private final CurrentUserService currentUsers;
    private final ArenaDashboardService dashboards;
    private final ArenaPredictionService predictions;
    private final PointWalletService wallets;
    private final ArenaPoolRankingService pools;
    private final ArenaNotificationService notifications;

    public ArenaUserController(CurrentUserService currentUsers, ArenaDashboardService dashboards,
                               ArenaPredictionService predictions, PointWalletService wallets,
                               ArenaPoolRankingService pools, ArenaNotificationService notifications) {
        this.currentUsers = currentUsers; this.dashboards = dashboards; this.predictions = predictions;
        this.wallets = wallets; this.pools = pools; this.notifications = notifications;
    }

    @GetMapping("/dashboard") public DashboardResponse dashboard(@AuthenticationPrincipal UserDetails details) { return dashboards.dashboard(user(details)); }
    @GetMapping("/predictions") public List<PredictionResponse> predictions(@AuthenticationPrincipal UserDetails details) { return predictions.list(user(details)); }
    @PostMapping("/predictions") @ResponseStatus(HttpStatus.CREATED)
    public PredictionResponse place(@Valid @RequestBody PlacePredictionRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @AuthenticationPrincipal UserDetails details) {
        return predictions.place(request, idempotencyKey, user(details));
    }
    @PostMapping("/predictions/{id}/cancel") public PredictionResponse cancel(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return predictions.cancel(id, user(details)); }
    @GetMapping("/wallet") public WalletResponse wallet(@AuthenticationPrincipal UserDetails details) { return wallets.wallet(user(details)); }
    @GetMapping("/wallet/transactions") public List<PointTransactionResponse> transactions(@AuthenticationPrincipal UserDetails details) { return wallets.transactions(user(details)); }
    @GetMapping("/pools") public List<PoolResponse> pools(@AuthenticationPrincipal UserDetails details) { return pools.list(user(details)); }
    @GetMapping("/pools/{id}") public PoolResponse pool(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return pools.get(id, user(details)); }
    @PostMapping("/pools") @ResponseStatus(HttpStatus.CREATED)
    public PoolResponse createPool(@Valid @RequestBody PoolRequest request, @AuthenticationPrincipal UserDetails details) { return pools.create(request, user(details)); }
    @PostMapping("/pools/join") public PoolResponse joinPool(@Valid @RequestBody JoinPoolRequest request, @AuthenticationPrincipal UserDetails details) { return pools.join(request.inviteCode(), user(details)); }
    @PostMapping("/pools/{id}/join") public PoolResponse joinPublicPool(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return pools.joinPublic(id, user(details)); }
    @PostMapping("/pools/{id}/leave") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leavePool(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { pools.leave(id, user(details)); }
    @GetMapping("/pools/{id}/ranking") public List<RankingRow> poolRanking(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return pools.poolRanking(id, user(details)); }
    @GetMapping("/rankings")
    public List<RankingRow> ranking(@RequestParam(defaultValue = "ALL") String period,
                                    @RequestParam(defaultValue = "GLOBAL") String scope,
                                    @RequestParam(required = false) String sport,
                                    @AuthenticationPrincipal UserDetails details) {
        return pools.ranking(user(details), rankingPeriod(period), rankingScope(scope), sport);
    }
    @GetMapping("/notifications") public List<NotificationResponse> notifications(@AuthenticationPrincipal UserDetails details) { return notifications.list(user(details)); }
    @PatchMapping("/notifications/{id}/read") public NotificationResponse read(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return notifications.read(id, user(details)); }
    @PatchMapping("/notifications/read-all") public Map<String, Integer> readAll(@AuthenticationPrincipal UserDetails details) { return Map.of("updated", notifications.readAll(user(details))); }
    private User user(UserDetails details) { return currentUsers.from(details); }
    private RankingPeriod rankingPeriod(String value) {
        String normalized = value == null ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if ("ALL_TIME".equals(normalized)) normalized = "ALL";
        try { return RankingPeriod.valueOf(normalized); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Período de ranking inválido."); }
    }
    private RankingScope rankingScope(String value) {
        try { return RankingScope.valueOf(value == null ? "GLOBAL" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Escopo de ranking inválido."); }
    }
}

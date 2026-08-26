package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ArenaAdminController {
    private final ArenaCatalogService catalog;
    private final AdminEventResultService eventResults;
    private final ArenaPredictionService predictions;
    private final ArenaDashboardService dashboards;
    private final DemoLiveEventService live;
    public ArenaAdminController(ArenaCatalogService catalog, AdminEventResultService eventResults,
                                ArenaPredictionService predictions,
                                ArenaDashboardService dashboards, DemoLiveEventService live) {
        this.catalog = catalog; this.eventResults = eventResults; this.predictions = predictions;
        this.dashboards = dashboards; this.live = live;
    }
    @GetMapping("/dashboard") public AdminDashboardResponse dashboard() { return dashboards.adminDashboard(); }
    @GetMapping("/sports") public List<SportResponse> sports() { return catalog.listSports(true); }
    @PostMapping("/sports") @ResponseStatus(HttpStatus.CREATED) public SportResponse createSport(@Valid @RequestBody SportRequest request) { return catalog.saveSport(null, request); }
    @PutMapping("/sports/{id}") public SportResponse updateSport(@PathVariable Long id, @Valid @RequestBody SportRequest request) { return catalog.saveSport(id, request); }
    @PostMapping("/championships") @ResponseStatus(HttpStatus.CREATED) public ChampionshipResponse createChampionship(@Valid @RequestBody ChampionshipRequest request) { return catalog.saveChampionship(null, request); }
    @PutMapping("/championships/{id}") public ChampionshipResponse updateChampionship(@PathVariable Long id, @Valid @RequestBody ChampionshipRequest request) { return catalog.saveChampionship(id, request); }
    @GetMapping("/competitors") public List<CompetitorResponse> competitors(@RequestParam(required = false) Long sportId) { return catalog.listCompetitors(sportId); }
    @PostMapping("/competitors") @ResponseStatus(HttpStatus.CREATED) public CompetitorResponse createCompetitor(@Valid @RequestBody CompetitorRequest request) { return catalog.saveCompetitor(null, request); }
    @PutMapping("/competitors/{id}") public CompetitorResponse updateCompetitor(@PathVariable Long id, @Valid @RequestBody CompetitorRequest request) { return catalog.saveCompetitor(id, request); }
    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED) public EventResponse createEvent(@Valid @RequestBody EventRequest request) { return catalog.saveEvent(null, request); }
    @PutMapping("/events/{id}") public EventResponse updateEvent(@PathVariable Long id, @Valid @RequestBody EventRequest request) { return catalog.saveEvent(id, request); }
    @PutMapping("/events/{id}/result")
    public EventResponse result(@PathVariable Long id, @Valid @RequestBody EventResultRequest request,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return eventResults.record(id, request, idempotencyKey);
    }
    @PutMapping("/events/{id}/classification")
    public EventResponse classification(@PathVariable Long id,
                                        @Valid @RequestBody EventClassificationRequest request,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return eventResults.recordClassification(id, request, idempotencyKey);
    }
    @PostMapping("/events/{id}/cancel") public Map<String, Integer> cancelEvent(@PathVariable Long id) { return Map.of("refundedPredictions", predictions.cancelEvent(id)); }
    @PostMapping("/markets") @ResponseStatus(HttpStatus.CREATED) public MarketResponse createMarket(@Valid @RequestBody MarketRequest request) { return catalog.saveMarket(null, request); }
    @PutMapping("/markets/{id}") public MarketResponse updateMarket(@PathVariable Long id, @Valid @RequestBody MarketRequest request) { return catalog.saveMarket(id, request); }
    @PatchMapping("/markets/{id}/status") public MarketResponse marketStatus(@PathVariable Long id, @Valid @RequestBody MarketStatusRequest request) { return catalog.changeMarketStatus(id, request.status()); }
    @PostMapping("/markets/{id}/settle") public SettlementResponse settle(@PathVariable Long id, @Valid @RequestBody SettleMarketRequest request) { return predictions.settleMarket(id, request.correctOptionKey()); }
    @PostMapping("/markets/{id}/cancel") public Map<String, Integer> cancelMarket(@PathVariable Long id) { return Map.of("refundedPredictions", predictions.cancelMarket(id)); }
    @PostMapping("/demo/live/refresh") public Map<String, Object> refreshDemoLive() { return live.refresh(); }
}

package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.ArenaCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ArenaCatalogController {
    private final ArenaCatalogService catalog;
    public ArenaCatalogController(ArenaCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/sports") public List<SportResponse> sports() { return catalog.listSports(false); }
    @GetMapping("/championships") public List<ChampionshipResponse> championships(@RequestParam(required = false) String sport) { return catalog.listChampionships(sport); }
    @GetMapping("/events") public List<EventResponse> events(@RequestParam(required = false) EventStatus status,
                                                              @RequestParam(required = false) String sport,
                                                              @RequestParam(required = false) Boolean featured) {
        return catalog.listEvents(status, sport, featured);
    }
    @GetMapping("/events/live") public List<EventResponse> live() { return catalog.liveEvents(); }
    @GetMapping("/events/{id}") public EventResponse event(@PathVariable Long id) { return catalog.eventResponse(id); }
}

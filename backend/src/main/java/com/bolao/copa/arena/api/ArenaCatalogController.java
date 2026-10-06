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
    @GetMapping(value="/events",params="page")
    public org.springframework.data.domain.Page<EventResponse> page(@RequestParam(required=false) EventStatus status,
            @RequestParam(required=false) String sport,@RequestParam(required=false) Boolean featured,@RequestParam(defaultValue="") String q,
            @RequestParam(required=false) String source,@RequestParam(required=false) Long championshipId,
            @RequestParam(required=false) java.time.Instant from,@RequestParam(required=false) java.time.Instant to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size) {
        return catalog.eventPage(status,sport,featured,q,source,championshipId,from,to,page,size);
    }
    @GetMapping("/events/{id}") public EventResponse event(@PathVariable Long id) { return catalog.eventResponse(id); }
}

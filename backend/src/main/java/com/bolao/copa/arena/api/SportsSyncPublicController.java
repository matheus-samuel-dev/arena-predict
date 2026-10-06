package com.bolao.copa.arena.api;

import com.bolao.copa.arena.service.sync.SportsSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Participant-safe metadata; operational errors and configuration stay in the admin endpoint. */
@RestController
@RequestMapping("/api/sports-sync")
public class SportsSyncPublicController {
    private final SportsSyncService sync;
    private com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator coordinator;
    public SportsSyncPublicController(SportsSyncService sync) { this.sync=sync; }
    @org.springframework.beans.factory.annotation.Autowired
    public SportsSyncPublicController(SportsSyncService sync,com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator coordinator) { this.sync=sync;this.coordinator=coordinator; }
    @GetMapping("/status")
    public SportsSyncService.Summary status() { return coordinator==null?sync.summary():coordinator.summary(); }
}

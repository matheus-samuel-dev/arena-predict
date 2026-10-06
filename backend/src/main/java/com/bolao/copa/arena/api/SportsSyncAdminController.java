package com.bolao.copa.arena.api;

import com.bolao.copa.arena.service.sync.SportsSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/sports-sync")
@PreAuthorize("hasRole('ADMIN')")
public class SportsSyncAdminController {
    private final SportsSyncService sync;
    private final com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator coordinator;
    public SportsSyncAdminController(SportsSyncService sync,com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator coordinator) { this.sync=sync;this.coordinator=coordinator; }
    @GetMapping("/status")
    public SportsSyncService.Status status() { return coordinator.defaultStatus(); }
    @GetMapping("/providers")
    public java.util.List<com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator.ProviderView> providers() { return coordinator.providers(); }
}

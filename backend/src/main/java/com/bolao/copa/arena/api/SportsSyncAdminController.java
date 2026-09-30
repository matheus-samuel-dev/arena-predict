package com.bolao.copa.arena.api;

import com.bolao.copa.arena.service.sync.SportsSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/sports-sync")
@PreAuthorize("hasRole('ADMIN')")
public class SportsSyncAdminController {
    private final SportsSyncService sync;
    public SportsSyncAdminController(SportsSyncService sync) { this.sync=sync; }
    @GetMapping("/status")
    public SportsSyncService.Status status() { return sync.status(); }
}

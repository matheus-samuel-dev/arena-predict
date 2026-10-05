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
    public SportsSyncPublicController(SportsSyncService sync) { this.sync=sync; }
    @GetMapping("/status")
    public SportsSyncService.Summary status() { return sync.summary(); }
}

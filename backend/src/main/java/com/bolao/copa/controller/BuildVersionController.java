package com.bolao.copa.controller;

import com.bolao.copa.arena.service.VirtualMultiplierService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public artifact identity only; never includes environment or credentials. */
@RestController
public class BuildVersionController {
    private final String revision;

    public BuildVersionController(@Value("${APP_BUILD_REVISION:unknown}") String revision) {
        this.revision = revision.matches("[a-f0-9]{40}") ? revision : "unknown";
    }

    @GetMapping("/api/version")
    public Map<String, String> version() {
        return Map.of("revision", revision, "pricingModel", VirtualMultiplierService.VERSION);
    }
}

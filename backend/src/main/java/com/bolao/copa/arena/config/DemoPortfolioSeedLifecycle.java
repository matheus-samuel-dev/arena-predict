package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.demo.DemoPortfolioSeedService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name={"app.demo.enabled", "app.demo.controlled-enabled", "app.demo.portfolio-seed-enabled"}, havingValue="true")
public class DemoPortfolioSeedLifecycle {
    private final DemoPortfolioSeedService seed;
    public DemoPortfolioSeedLifecycle(DemoPortfolioSeedService seed) { this.seed = seed; }
    @EventListener(ApplicationReadyEvent.class) @Order(500)
    public void initialize() { seed.seed(); }
}

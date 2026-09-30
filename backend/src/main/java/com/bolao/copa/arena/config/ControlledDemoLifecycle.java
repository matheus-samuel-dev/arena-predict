package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.demo.DemoScenarioService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name={"app.demo.enabled", "app.demo.controlled-enabled"}, havingValue="true")
public class ControlledDemoLifecycle {
    private final DemoScenarioService demo;
    public ControlledDemoLifecycle(DemoScenarioService demo) { this.demo = demo; }
    @EventListener(ApplicationReadyEvent.class) @Order(400)
    public void initialize() { demo.initialize(); }
    @Scheduled(fixedDelayString="${app.demo.maintenance-ms:60000}", initialDelayString="${app.demo.maintenance-ms:60000}")
    public void maintain() { demo.maintain(); }
}

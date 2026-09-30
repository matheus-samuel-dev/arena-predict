package com.bolao.copa.arena.api;

import com.bolao.copa.arena.service.demo.DemoScenarioService;
import com.bolao.copa.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name={"app.demo.enabled", "app.demo.controlled-enabled"}, havingValue="true")
public class DemoScenarioController {
    private final DemoScenarioService demo;
    private final CurrentUserService users;
    public DemoScenarioController(DemoScenarioService demo, CurrentUserService users) { this.demo=demo; this.users=users; }
    @GetMapping("/scenario")
    public DemoScenarioDtos.Scenario scenario(@AuthenticationPrincipal UserDetails user) { return demo.scenario(users.from(user)); }
    @PostMapping("/events/{id}/start")
    public DemoScenarioDtos.Scenario start(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) { return demo.start(id, users.from(user)); }
    @PostMapping("/events/{id}/result")
    public DemoScenarioDtos.Scenario result(@PathVariable Long id, @Valid @RequestBody DemoScenarioDtos.Result score, @AuthenticationPrincipal UserDetails user) {
        return demo.finish(id, score, users.from(user));
    }
    @PostMapping("/reset")
    public DemoScenarioDtos.Scenario reset(@Valid @RequestBody DemoScenarioDtos.Reset request, @AuthenticationPrincipal UserDetails user) {
        return demo.reset(request.expectedGeneration(), users.from(user));
    }
}

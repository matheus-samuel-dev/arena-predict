package com.bolao.copa.controller;

import com.bolao.copa.dto.AuthDtos.AuthResponse;
import com.bolao.copa.dto.AuthDtos.DemoAccessRequest;
import com.bolao.copa.service.DemoAuthService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoAuthController {
    private final DemoAuthService demoAuthService;

    public DemoAuthController(DemoAuthService demoAuthService) {
        this.demoAuthService = demoAuthService;
    }

    @PostMapping("/demo")
    public AuthResponse access(@Valid @RequestBody DemoAccessRequest request) {
        return demoAuthService.access(request);
    }
}

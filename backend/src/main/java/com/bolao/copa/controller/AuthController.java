package com.bolao.copa.controller;

import com.bolao.copa.dto.AuthDtos.AuthResponse;
import com.bolao.copa.dto.AuthDtos.LoginRequest;
import com.bolao.copa.dto.AuthDtos.RegisterRequest;
import com.bolao.copa.dto.AuthDtos.UserResponse;
import com.bolao.copa.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails details) {
        return authService.me(details);
    }

    /**
     * JWT authentication is stateless: the client completes logout by deleting
     * its token. This endpoint validates the current session and clears the
     * request security context; no server-side session or financial state exists.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        SecurityContextHolder.clearContext();
    }
}

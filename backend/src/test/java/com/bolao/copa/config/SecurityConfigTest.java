package com.bolao.copa.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolao.copa.controller.AuthController;
import com.bolao.copa.controller.DemoAuthController;
import com.bolao.copa.dto.AuthDtos.AuthResponse;
import com.bolao.copa.dto.AuthDtos.UserResponse;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.security.CustomUserDetailsService;
import com.bolao.copa.security.JwtAuthenticationFilter;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.security.RestAccessDeniedHandler;
import com.bolao.copa.security.RestAuthenticationEntryPoint;
import com.bolao.copa.service.AuthService;
import com.bolao.copa.service.DemoAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {AuthController.class, DemoAuthController.class, RbacProbeController.class})
@Import({
        SecurityConfig.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-with-at-least-thirty-two-bytes-123456",
        "app.jwt.expiration-minutes=60",
        "app.demo.enabled=true"
})
class SecurityConfigTest {
    static {
        // The desktop runtime uses a newer JDK than the project's Java 21 target.
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockBean AuthService authService;
    @MockBean DemoAuthService demoAuthService;
    @MockBean CustomUserDetailsService userDetailsService;

    @Test
    void apiLoginAndLegacyAliasArePublic() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse(
                "token", null, "Admin", "admin@arenapredict.com", UserRole.ADMIN
        ));
        var body = "{\"email\":\"admin@arenapredict.com\",\"password\":\"unit-test-password\"}";

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void demoAccessIsPublicButAcceptsOnlyAProfile() throws Exception {
        when(demoAuthService.access(any())).thenReturn(new AuthResponse(
                "regular-jwt", null, "Jogador Demo", "jogador@arenapredict.com", UserRole.PARTICIPANTE
        ));

        mockMvc.perform(post("/api/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"PARTICIPANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("regular-jwt"))
                .andExpect(jsonPath("$.role").value("PARTICIPANTE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verifique os dados enviados e tente novamente."));
    }

    @Test
    void protectedEndpointWithoutOrWithInvalidTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/probe/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/probe/admin").header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void participantOnAdminEndpointReturns403() throws Exception {
        var token = tokenFor("jogador@arenapredict.com", UserRole.PARTICIPANTE);
        mockUser("jogador@arenapredict.com", "ROLE_PARTICIPANTE", "ROLE_USER");

        mockMvc.perform(get("/probe/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanAccessAdminEndpoint() throws Exception {
        var token = tokenFor("admin@arenapredict.com", UserRole.ADMIN);
        mockUser("admin@arenapredict.com", "ROLE_ADMIN");

        mockMvc.perform(get("/probe/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void activeSessionCanBeRestoredAndLoggedOut() throws Exception {
        var email = "jogador@arenapredict.com";
        var token = tokenFor(email, UserRole.PARTICIPANTE);
        mockUser(email, "ROLE_PARTICIPANTE", "ROLE_USER");
        when(authService.me(any())).thenReturn(
                new UserResponse(null, "Jogador Demo", email, UserRole.PARTICIPANTE,
                        "https://example.test/avatar.png")
        );

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARTICIPANTE"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.test/avatar.png"));
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String tokenFor(String email, UserRole role) {
        var user = new User();
        user.setName("Demo");
        user.setEmail(email);
        user.setPasswordHash("ignored");
        user.setRole(role);
        return jwtService.generate(user);
    }

    private void mockUser(String email, String... authorities) {
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("ignored")
                .authorities(authorities)
                .build();
        when(userDetailsService.loadUserByUsername(email)).thenReturn(details);
    }

}

@RestController
class RbacProbeController {
    @GetMapping("/probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    java.util.Map<String, Boolean> admin() {
        return java.util.Map.of("ok", true);
    }
}

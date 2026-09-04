package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolao.copa.entity.UserRole;
import com.bolao.copa.security.CustomUserDetailsService;
import com.bolao.copa.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=https://arena-predict.18-231-73-103.sslip.io,http://localhost:5173",
        "app.demo.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoQuickAccessCorsIntegrationTest {
    private static final String PUBLIC_ORIGIN = "https://arena-predict.18-231-73-103.sslip.io";
    private static final String BLOCKED_ORIGIN = "https://not-allowed.example";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired CustomUserDetailsService userDetailsService;

    @Test
    void publicOriginCanQuickLoginAsParticipantAndUseParticipantSession() throws Exception {
        var login = quickLogin("PARTICIPANT", UserRole.PARTICIPANTE);
        var claims = jwtService.parse(login.token());

        assertThat(claims.subject()).isEqualToIgnoringCase(login.email());
        assertThat(claims.role()).isEqualTo(UserRole.PARTICIPANTE);
        assertThat(authoritiesFor(claims.subject()))
                .contains("ROLE_PARTICIPANTE", "ROLE_USER", "ROLE_PLAYER")
                .doesNotContain("ROLE_ADMIN");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN))
                .andExpect(jsonPath("$.email").value(login.email()))
                .andExpect(jsonPath("$.role").value("PARTICIPANTE"));

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token()))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN));
    }

    @Test
    void publicOriginCanQuickLoginAsAdministratorAndUseAdminSession() throws Exception {
        var login = quickLogin("ADMIN", UserRole.ADMIN);
        var claims = jwtService.parse(login.token());

        assertThat(claims.subject()).isEqualToIgnoringCase(login.email());
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
        assertThat(authoritiesFor(claims.subject()))
                .containsExactly("ROLE_ADMIN");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN))
                .andExpect(jsonPath("$.email").value(login.email()))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN));
    }

    @Test
    void unlistedOriginIsRejectedBeforeQuickLogin() throws Exception {
        mockMvc.perform(post("/api/auth/demo")
                        .header(HttpHeaders.ORIGIN, BLOCKED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"PARTICIPANT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private QuickLogin quickLogin(String profile, UserRole expectedRole) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo")
                        .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"" + profile + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PUBLIC_ORIGIN))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value(expectedRole.name()))
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new QuickLogin(body.get("token").asText(), body.get("email").asText());
    }

    private Set<String> authoritiesFor(String email) {
        return userDetailsService.loadUserByUsername(email).getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }

    private record QuickLogin(String token, String email) {
    }
}

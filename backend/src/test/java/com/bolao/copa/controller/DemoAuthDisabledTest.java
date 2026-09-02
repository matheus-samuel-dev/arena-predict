package com.bolao.copa.controller;

import com.bolao.copa.security.CustomUserDetailsService;
import com.bolao.copa.security.JwtService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DemoAuthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.demo.enabled=false")
class DemoAuthDisabledTest {
    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    @Test
    void routeDoesNotExistWhenDemoModeIsDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"PARTICIPANT\"}"))
                .andExpect(status().isNotFound());
    }
}

package com.bolao.copa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (SecurityContextHolder.getContext().getAuthentication() == null && isBearer(header)) {
            authenticate(header.substring(BEARER_PREFIX.length()), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String rawToken, HttpServletRequest request) {
        try {
            var token = jwtService.parse(rawToken.trim());
            var userDetails = userDetailsService.loadUserByUsername(token.subject());
            if (!jwtService.isValidFor(token, userDetails)) {
                SecurityContextHolder.clearContext();
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException invalidOrExpiredToken) {
            // Invalid credentials are intentionally not logged or exposed. Protected
            // endpoints will be answered by the configured 401 entry point.
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isBearer(String header) {
        return header != null
                && header.length() > BEARER_PREFIX.length()
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }
}

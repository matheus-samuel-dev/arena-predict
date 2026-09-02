package com.bolao.copa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-side demo identities. Passwords are optional deployment secrets used
 * only to provision the two persisted accounts; quick access never receives or
 * returns them.
 */
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(
        boolean enabled,
        String adminEmail,
        String adminPassword,
        String participantEmail,
        String participantPassword
) {
    private static final String DEFAULT_ADMIN_EMAIL = "admin@arenapredict.com";
    private static final String DEFAULT_PARTICIPANT_EMAIL = "jogador@arenapredict.com";

    public DemoProperties {
        adminEmail = valueOrDefault(adminEmail, DEFAULT_ADMIN_EMAIL);
        participantEmail = valueOrDefault(participantEmail, DEFAULT_PARTICIPANT_EMAIL);
        adminPassword = valueOrEmpty(adminPassword);
        participantPassword = valueOrEmpty(participantPassword);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

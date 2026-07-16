package com.bolao.copa.entity;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    USER,
    PARTICIPANTE;

    /**
     * USER is retained to read legacy rows and tokens. New API contracts use
     * PARTICIPANTE consistently.
     */
    public UserRole canonical() {
        return this == USER ? PARTICIPANTE : this;
    }

    public String authority() {
        return "ROLE_" + canonical().name();
    }

    public static UserRole fromExternalValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Perfil de acesso ausente.");
        }

        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }

        return switch (normalized) {
            case "ADMIN", "ADMINISTRATOR", "ADMINISTRADOR" -> ADMIN;
            case "USER", "PLAYER", "PARTICIPANT", "PARTICIPANTE", "JOGADOR" -> PARTICIPANTE;
            default -> throw new IllegalArgumentException("Perfil de acesso desconhecido.");
        };
    }
}

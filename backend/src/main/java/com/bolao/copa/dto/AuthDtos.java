package com.bolao.copa.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.bolao.copa.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe seu nome.")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
            String name,

            @Email(message = "Informe um e-mail válido.")
            @NotBlank(message = "Informe seu e-mail.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email,

            @NotBlank(message = "Informe uma senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            String password,

            // Accepted only for backwards-compatible payloads. Public registration
            // always creates a PARTICIPANTE; clients cannot self-assign ADMIN.
            UserRole role
    ) {
    }

    public record LoginRequest(
            @Email(message = "Informe um e-mail válido.")
            @NotBlank(message = "Informe seu e-mail.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email,

            @NotBlank(message = "Informe uma senha.")
            @Size(max = 200, message = "A senha informada é inválida.")
            String password
    ) {
    }

    public record DemoAccessRequest(
            @NotNull(message = "Escolha um perfil de demonstração.")
            DemoProfile profile
    ) {
    }

    public enum DemoProfile {
        PARTICIPANT(UserRole.PARTICIPANTE),
        ADMIN(UserRole.ADMIN);

        private final UserRole userRole;

        DemoProfile(UserRole userRole) {
            this.userRole = userRole;
        }

        public UserRole userRole() {
            return userRole;
        }

        @JsonCreator
        public static DemoProfile fromJson(String value) {
            if (value == null) return null;
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "PARTICIPANT", "PARTICIPANTE" -> PARTICIPANT;
                case "ADMIN" -> ADMIN;
                default -> throw new IllegalArgumentException("Perfil de demonstração inválido.");
            };
        }
    }

    public record AuthResponse(
            @NotNull String token,
            Long userId,
            String name,
            String email,
            UserRole role
    ) {
    }

    public record UserResponse(Long userId, String name, String email, UserRole role, String avatarUrl) {
    }
}

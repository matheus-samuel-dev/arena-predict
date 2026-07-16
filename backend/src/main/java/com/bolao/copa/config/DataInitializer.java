package com.bolao.copa.config;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DataInitializer {
    @Bean
    CommandLineRunner seed(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            var legacyRoleUsers = userRepository.findAllByRole(UserRole.USER);
            legacyRoleUsers.forEach(user -> user.setRole(UserRole.PARTICIPANTE));
            if (!legacyRoleUsers.isEmpty()) {
                userRepository.saveAll(legacyRoleUsers);
            }

            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "admin@arenapredict.com",
                    "Administrador Demo",
                    "Admin@123",
                    UserRole.ADMIN,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "jogador@arenapredict.com",
                    "Jogador Demo",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );

            // Compatibility accounts keep existing passwords and data ownership.
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "admin@bolao.com",
                    "Administrador",
                    "123456",
                    UserRole.ADMIN,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "user@bolao.com",
                    "Participante Teste",
                    "123456",
                    UserRole.PARTICIPANTE,
                    false
            );

        };
    }

    private User ensureUser(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            String email,
                            String name,
                            String password,
                            UserRole role,
                            boolean enforceDemoCredentials) {
        var existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            var created = new User();
            created.setName(name);
            created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode(password));
            created.setRole(role);
            return userRepository.save(created);
        }

        var user = existing.get();
        var changed = false;
        if (user.getRole().canonical() != role.canonical() || user.getRole() != role) {
            user.setRole(role.canonical());
            changed = true;
        }
        if (enforceDemoCredentials && !name.equals(user.getName())) {
            user.setName(name);
            changed = true;
        }
        if (enforceDemoCredentials
                && (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
            user.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
        }
        return changed ? userRepository.save(user) : user;
    }
}

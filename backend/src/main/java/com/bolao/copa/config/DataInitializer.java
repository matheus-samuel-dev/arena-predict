package com.bolao.copa.config;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DataInitializer {
    @Bean
    CommandLineRunner seed(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           TransactionTemplate transactionTemplate) {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            var legacyRoleUsers = userRepository.findAllByRole(UserRole.USER);
            legacyRoleUsers.forEach(user -> user.setRole(UserRole.PARTICIPANTE));
            if (!legacyRoleUsers.isEmpty()) {
                userRepository.saveAll(legacyRoleUsers);
            }

            // Preserve ownership and history from the previous portfolio schema,
            // but remove confusing weak-credential aliases from the demo surface.
            migrateLegacyUser(userRepository, "admin@bolao.com", "marina.costa@arenapredict.com", "Marina Costa");
            migrateLegacyUser(userRepository, "user@bolao.com", "rafael.lima@arenapredict.com", "Rafael Lima");

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
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "marina.costa@arenapredict.com",
                    "Marina Costa",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "rafael.lima@arenapredict.com",
                    "Rafael Lima",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "beatriz.nunes@arenapredict.com",
                    "Beatriz Nunes",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "camila.rocha@arenapredict.com",
                    "Camila Rocha",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "lucas.almeida@arenapredict.com",
                    "Lucas Almeida",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "ana.ribeiro@arenapredict.com",
                    "Ana Ribeiro",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "diego.ferreira@arenapredict.com",
                    "Diego Ferreira",
                    "Jogador@123",
                    UserRole.PARTICIPANTE,
                    true
            );

        });
    }

    private void migrateLegacyUser(UserRepository userRepository,
                                   String legacyEmail,
                                   String preferredEmail,
                                   String professionalName) {
        var legacy = userRepository.findByEmailIgnoreCase(legacyEmail).orElse(null);
        if (legacy == null) return;

        var preferredOwner = userRepository.findByEmailIgnoreCase(preferredEmail).orElse(null);
        String migratedEmail = preferredOwner == null || preferredOwner.getId().equals(legacy.getId())
                ? preferredEmail
                : "conta.migrada." + legacy.getId() + "@arenapredict.com";
        legacy.setEmail(migratedEmail);
        legacy.setName(preferredOwner == null ? professionalName : "Participante Demo Migrado");
        legacy.setRole(UserRole.PARTICIPANTE);
        userRepository.save(legacy);
    }

    private User ensureUser(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            String email,
                            String name,
                            String password,
                            UserRole role,
                            boolean enforceDemoIdentity) {
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
        if (enforceDemoIdentity && !name.equals(user.getName())) {
            user.setName(name);
            changed = true;
        }
        // Existing credentials belong to the persisted account. Demo startup
        // may repair its presentation identity, but must never silently reset a
        // password that the user or operator has already changed.
        return changed ? userRepository.save(user) : user;
    }
}

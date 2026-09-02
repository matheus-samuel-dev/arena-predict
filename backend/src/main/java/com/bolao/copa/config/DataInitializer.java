package com.bolao.copa.config;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DataInitializer {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Bean
    CommandLineRunner seed(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           TransactionTemplate transactionTemplate,
                           DemoProperties demo) {
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
                    demo.adminEmail(),
                    "Administrador Demo",
                    demo.adminPassword(),
                    UserRole.ADMIN,
                    true,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    demo.participantEmail(),
                    "Jogador Demo",
                    demo.participantPassword(),
                    UserRole.PARTICIPANTE,
                    true,
                    true
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "marina.costa@arenapredict.com",
                    "Marina Costa",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "rafael.lima@arenapredict.com",
                    "Rafael Lima",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "beatriz.nunes@arenapredict.com",
                    "Beatriz Nunes",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "camila.rocha@arenapredict.com",
                    "Camila Rocha",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "lucas.almeida@arenapredict.com",
                    "Lucas Almeida",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "ana.ribeiro@arenapredict.com",
                    "Ana Ribeiro",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "diego.ferreira@arenapredict.com",
                    "Diego Ferreira",
                    "",
                    UserRole.PARTICIPANTE,
                    true,
                    false
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
                            boolean enforceDemoIdentity,
                            boolean synchronizeConfiguredPassword) {
        var existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            var created = new User();
            created.setName(name);
            created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode(
                    password == null || password.isBlank() ? randomBootstrapPassword() : password
            ));
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
        // A deployment can deliberately synchronize the two demo credentials
        // from secrets. With an empty secret, persisted credentials are kept.
        if (synchronizeConfiguredPassword && password != null && !password.isBlank()
                && !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
        }
        return changed ? userRepository.save(user) : user;
    }

    private String randomBootstrapPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

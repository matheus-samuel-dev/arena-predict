package com.bolao.copa.service;

import com.bolao.copa.config.DemoProperties;
import com.bolao.copa.arena.repository.PlayerProfileRepository;
import com.bolao.copa.dto.AuthDtos.AuthResponse;
import com.bolao.copa.dto.AuthDtos.DemoAccessRequest;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.security.DemoAccessPolicy;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a regular JWT for one of the two explicitly configured demo accounts.
 * It is not a generic impersonation API: callers select a profile, never an
 * identity, role, e-mail or set of authorities.
 */
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoAuthService {
    private final UserRepository users;
    private final JwtService jwtService;
    private final DemoProperties properties;
    private final PlayerProfileRepository playerProfiles;
    private final DemoAccessPolicy demoAccess;
    private final com.bolao.copa.arena.service.LiveTrainingService training;

    public DemoAuthService(UserRepository users, JwtService jwtService, DemoProperties properties, PlayerProfileRepository playerProfiles,
                           DemoAccessPolicy demoAccess, com.bolao.copa.arena.service.LiveTrainingService training) {
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
        this.playerProfiles = playerProfiles;
        this.demoAccess = demoAccess;
        this.training = training;
    }

    @Transactional
    public AuthResponse access(DemoAccessRequest request) {
        var expectedRole = request.profile().userRole();
        var configuredEmail = request.profile() == com.bolao.copa.dto.AuthDtos.DemoProfile.ADMIN
                ? properties.adminEmail()
                : properties.participantEmail();

        User user = users.findByEmailIgnoreCase(normalizeEmail(configuredEmail))
                .orElseThrow(DemoAccessUnavailableException::new);
        if (user.getRole() == null || user.getRole().canonical() != expectedRole || demoAccess.demoProfile(user) != request.profile()) {
            throw new DemoAccessUnavailableException();
        }
        if(request.training()) {
            demoAccess.requireDemoParticipant(user);
            String session=training.createSession();
            return new AuthResponse(jwtService.generateTraining(user,session),user.getId(),"Jogador Demo",user.getEmail(),
                    user.getRole().canonical(),null,request.profile(),true);
        }

        return new AuthResponse(
                jwtService.generate(user),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().canonical(),
                playerProfiles.findByUser(user).map(profile -> profile.getAvatarUrl()).orElse(null),
                demoAccess.demoProfile(user)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static final class DemoAccessUnavailableException extends RuntimeException {
        public DemoAccessUnavailableException() {
            super("O acesso demonstrativo está temporariamente indisponível.");
        }
    }
}

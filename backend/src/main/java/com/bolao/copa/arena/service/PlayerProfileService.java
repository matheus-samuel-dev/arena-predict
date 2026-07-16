package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ExperienceDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProfileService {
    private final PlayerProfileRepository profiles;
    private final PointWalletService walletService;
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public PlayerProfileService(PlayerProfileRepository profiles, PointWalletService walletService,
                                UserRepository users, PasswordEncoder passwords) {
        this.profiles = profiles; this.walletService = walletService; this.users = users; this.passwords = passwords;
    }

    @Transactional public ProfileResponse get(User user) { return response(ensure(user)); }

    @Transactional
    public ProfileResponse update(User user, ProfileUpdateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(user.getEmail()))
            throw new ArenaProblem.RuleViolation("A alteração de e-mail exige nova autenticação e ainda não está disponível neste perfil.");
        user.setName(request.name().trim());
        PlayerProfile profile = ensure(user);
        profile.setAvatarUrl(blankToNull(request.avatarUrl())); profile.setBio(blankToNull(request.bio()));
        profile.setFavoriteSports(request.favoriteSports() == null ? null : String.join(",", request.favoriteSports().stream().map(String::trim).filter(v -> !v.isBlank()).distinct().toList()));
        if (request.publicProfile() != null) profile.setPublicProfile(request.publicProfile());
        profile.touch(); users.save(user);
        return response(profile);
    }

    @Transactional
    public void changePassword(User user, PasswordUpdateRequest request) {
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash()))
            throw new ArenaProblem.RuleViolation("A senha atual está incorreta.");
        if (passwords.matches(request.newPassword(), user.getPasswordHash()))
            throw new ArenaProblem.RuleViolation("A nova senha deve ser diferente da senha atual.");
        user.setPasswordHash(passwords.encode(request.newPassword())); users.save(user);
    }

    @Transactional
    public PreferenceResponse preferences(User user, PreferenceUpdateRequest request) {
        PlayerProfile profile = ensure(user);
        if (request.theme() != null) profile.setTheme(request.theme());
        if (request.language() != null) profile.setLanguage(request.language());
        if (request.notifications() != null) profile.setNotificationsEnabled(request.notifications());
        if (request.publicProfile() != null) profile.setPublicProfile(request.publicProfile());
        profile.touch();
        return new PreferenceResponse(profile.getTheme(), profile.getLanguage(), profile.isNotificationsEnabled(), profile.isPublicProfile());
    }

    @Transactional public PlayerProfile ensure(User user) {
        return profiles.findByUser(user).orElseGet(() -> { PlayerProfile value = new PlayerProfile(); value.setUser(user); return profiles.save(value); });
    }

    private ProfileResponse response(PlayerProfile profile) {
        var wallet = walletService.wallet(profile.getUser());
        long xp = wallet.lifetimeEarned();
        return new ProfileResponse(profile.getUser().getId(), profile.getUser().getName(), profile.getUser().getEmail(),
                profile.getUser().getRole().name(), profile.getAvatarUrl(), profile.getBio(), sports(profile.getFavoriteSports()),
                profile.getTheme(), profile.getLanguage(), profile.isNotificationsEnabled(), profile.isPublicProfile(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, xp / 5_000 + 1)), xp, wallet.balance());
    }
    private List<String> sports(String value) { return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(",")); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

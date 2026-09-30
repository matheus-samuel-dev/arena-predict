package com.bolao.copa.support;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;

/** Explicit regular accounts: public quick-access identities must never stand in for full privileges. */
public final class RegularTestUsers {
    public static final String PARTICIPANT_EMAIL = "participant.contracts@example.test";
    public static final String ADMIN_EMAIL = "administrator.contracts@example.test";

    private RegularTestUsers() { }

    public static User participant(UserRepository users) {
        return account(users, PARTICIPANT_EMAIL, UserRole.PARTICIPANTE);
    }

    /** Use a distinct identity when the test asserts first-use rewards or exact balances. */
    public static User freshParticipant(UserRepository users) {
        return account(users, "participant." + java.util.UUID.randomUUID() + "@example.test",
                UserRole.PARTICIPANTE);
    }

    public static User admin(UserRepository users) {
        return account(users, ADMIN_EMAIL, UserRole.ADMIN);
    }

    private static User account(UserRepository users, String email, UserRole role) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            var user = new User();
            user.setName(role == UserRole.ADMIN ? "Administrador regular de teste" : "Participante regular de teste");
            user.setEmail(email);
            user.setRole(role);
            user.setPasswordHash("unused-authentication-fixture");
            return users.saveAndFlush(user);
        });
    }
}

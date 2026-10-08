package com.bolao.copa.security;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.config.DemoProperties;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.dto.AuthDtos.DemoProfile;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Demo identity comes from server configuration and persisted users, never request flags or JWT claims. */
public final class DemoAccessPolicy {
    private final DemoProperties properties;

    public DemoAccessPolicy(DemoProperties properties) { this.properties = properties; }

    /** Public examples cannot enumerate a registered visitor's wallet/profile. */
    public java.util.Set<String> demonstrationEmails() {
        var emails=new java.util.HashSet<String>();
        DemoParticipantCatalog.participants().forEach(p->emails.add(p.email().toLowerCase(java.util.Locale.ROOT)));
        emails.add(properties.adminEmail().trim().toLowerCase(java.util.Locale.ROOT));
        emails.add(properties.participantEmail().trim().toLowerCase(java.util.Locale.ROOT));
        return java.util.Set.copyOf(emails);
    }

    public boolean isDemoAccount(User user) {
        return persisted(user) && isReservedDemoEmail(user.getEmail());
    }

    public boolean isDemoAdmin(User user) {
        return persisted(user) && sameEmail(user.getEmail(), properties.adminEmail()) && role(user, UserRole.ADMIN);
    }

    public boolean isDemoParticipant(User user) {
        return persisted(user) && sameEmail(user.getEmail(), properties.participantEmail()) && role(user, UserRole.PARTICIPANTE);
    }

    public DemoProfile demoProfile(User user) {
        if (isDemoAdmin(user)) return DemoProfile.ADMIN;
        if (isDemoParticipant(user)) return DemoProfile.PARTICIPANT;
        return null;
    }

    /** Reserved even while quick access is disabled, so registration cannot capture a future Demo identity. */
    public boolean isReservedDemoEmail(String email) {
        return sameEmail(email, properties.adminEmail()) || sameEmail(email, properties.participantEmail());
    }

    public void requireDemoAdmin(User user) {
        if (!properties.enabled() || !isDemoAdmin(user)) throw denied();
    }

    public void requireDemoParticipant(User user) {
        if (!properties.enabled() || !isDemoParticipant(user)) throw denied();
    }

    /** Called by normal prediction commands; internal scenario seeding has a separate validated service entry point. */
    public void requirePredictionAccess(User user, ArenaEvent event) {
        boolean controlled = event.getChampionship() != null && event.getChampionship().isDemoManaged();
        if (controlled) {
            requireDemoParticipant(user);
            if (!event.isDemo() || event.getExternalProvider() != null || event.getExternalId() != null
                    || event.isDemoArchived()) throw denied();
        } else if (isDemoAccount(user)) {
            requireDemoParticipant(user);
            if (!event.isDemo() || event.getExternalProvider() != null || event.getExternalId() != null
                    || event.isDemoArchived()) throw denied();
        }
    }

    /** Authentication is already checked against the database by JwtAuthenticationFilter. */
    public boolean allowsHttpRequest(Authentication authentication, String method, String path) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return false;
        String email = authentication.getName();
        boolean demo = isReservedDemoEmail(email);
        boolean admin = sameEmail(email, properties.adminEmail()) && hasAuthority(authentication, "ROLE_ADMIN");
        boolean participant = sameEmail(email, properties.participantEmail()) && hasAuthority(authentication, "ROLE_PARTICIPANTE");
        boolean reading = "GET".equals(method) || "HEAD".equals(method);
        if (path.equals("/api/demo") || path.startsWith("/api/demo/")) {
            if (!properties.enabled()) return false;
            if (reading) return (admin || participant) && path.equals("/api/demo/scenario");
            return admin && "POST".equals(method) && (path.equals("/api/demo/reset")
                    || path.matches("/api/demo/events/[1-9][0-9]*/(?:start|result)"));
        }
        if (!demo) return true;
        // Retain restrictions if quick access is disabled after a token was issued.
        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            return admin && properties.enabled() && reading && path.matches(
                    "/api/admin/(?:dashboard|sports|championships|competitors|events|markets|users|pools|scoring-rules|reports|audit|settings|moderation|achievements|challenges|notifications|sports-sync/(?:status|providers)|events/[1-9][0-9]*/market-templates)");
        }
        if (reading) return true;
        if ("POST".equals(method) && (path.equals("/api/auth/logout") || path.equals("/auth/logout"))) return true;
        if (!properties.enabled()) return false;
        // Own profile and notification commands retain their service-level ownership checks.
        if ("PATCH".equals(method) && (path.equals("/api/profile") || path.equals("/api/profile/preferences")
                || path.matches("/api/notifications/(?:[1-9][0-9]*/read|read-all)"))) return admin || participant;
        if (!participant) return false;
        if ("POST".equals(method)) return path.equals("/api/predictions")
                || path.matches("/api/predictions/[1-9][0-9]*/cancel")
                || path.equals("/api/pools") || path.equals("/api/pools/join")
                || path.matches("/api/pools/[1-9][0-9]*/(?:join|leave)")
                || path.equals("/api/community/posts")
                || path.matches("/api/community/posts/[1-9][0-9]*/(?:like|comments|reports)");
        return "DELETE".equals(method) && path.matches("/api/community/posts/[1-9][0-9]*(?:/like)?");
    }

    private static boolean persisted(User user) { return user != null && user.getId() != null; }
    private static boolean role(User user, UserRole expected) { return user.getRole() != null && user.getRole().canonical() == expected; }
    private static boolean sameEmail(String value, String expected) { return value != null && expected != null && value.trim().equalsIgnoreCase(expected.trim()); }
    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(value -> authority.equals(value.getAuthority()));
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Esta ação está restrita ao cenário demonstrativo e ao perfil autorizado."); }
}

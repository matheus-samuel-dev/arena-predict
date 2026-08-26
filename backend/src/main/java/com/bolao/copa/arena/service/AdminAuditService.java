package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.AdminAuditEvent;
import com.bolao.copa.arena.repository.AdminAuditRepository;
import com.bolao.copa.config.CorrelationIdContext;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final AdminAuditRepository audits;
    private final UserRepository users;

    public AdminAuditService(AdminAuditRepository audits, UserRepository users) {
        this.audits = audits;
        this.users = users;
    }

    @Transactional
    public AdminAuditEvent record(String action, String resourceType, Object resourceId, String summary) {
        Actor actor = currentActor();
        AdminAuditEvent event = new AdminAuditEvent(
                actor.id(), actor.name(), actor.role(), required(action, 80), required(resourceType, 60),
                resourceId == null ? null : sanitized(resourceId.toString(), 80), "SUCCESS",
                sanitized(summary, 500), sanitized(CorrelationIdContext.get(), 80), Instant.now()
        );
        return audits.save(event);
    }

    private Actor currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new Actor(null, "Sistema", "SYSTEM");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .map(user -> new Actor(user.getId(), user.getName(), user.getRole().canonical().name()))
                .orElseGet(() -> new Actor(null, sanitized(authentication.getName(), 120),
                        authentication.getAuthorities().stream().findFirst()
                                .map(value -> sanitized(value.getAuthority(), 40)).orElse("AUTHENTICATED")));
    }

    private String required(String value, int maxLength) {
        String sanitized = sanitized(value, maxLength);
        if (sanitized == null || sanitized.isBlank())
            throw new IllegalArgumentException("Metadado obrigatório de auditoria ausente.");
        return sanitized;
    }

    private String sanitized(String value, int maxLength) {
        if (value == null) return null;
        String clean = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return null;
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private record Actor(Long id, String name, String role) { }
}

package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.EventResponse;
import static com.bolao.copa.arena.api.ArenaDtos.EventClassificationRequest;
import static com.bolao.copa.arena.api.ArenaDtos.EventParticipantRequest;
import static com.bolao.copa.arena.api.ArenaDtos.EventParticipantResponse;
import static com.bolao.copa.arena.api.ArenaDtos.EventResultRequest;

import com.bolao.copa.arena.domain.AdminOperationKey;
import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.repository.AdminOperationKeyRepository;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates replay-safe result registration without coupling HTTP concerns to the catalog service. */
@Service
public class AdminEventResultService {
    private static final String OPERATION = "EVENT_RESULT";
    private static final String CLASSIFICATION_OPERATION = "EVENT_CLASSIFICATION";
    private static final int MAX_KEY_LENGTH = 80;

    private final ArenaEventRepository events;
    private final AdminOperationKeyRepository operationKeys;
    private final ArenaCatalogService catalog;

    public AdminEventResultService(ArenaEventRepository events, AdminOperationKeyRepository operationKeys,
                                   ArenaCatalogService catalog) {
        this.events = events;
        this.operationKeys = operationKeys;
        this.catalog = catalog;
    }

    @Transactional
    public EventResponse record(Long eventId, EventResultRequest request, String headerKey) {
        ArenaEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        String key = normalizedKey(headerKey);
        String fingerprint = fingerprint(request);

        if (key != null) {
            AdminOperationKey existing = operationKeys.findByOperationAndIdempotencyKey(OPERATION, key).orElse(null);
            if (existing != null) {
                if (!eventId.toString().equals(existing.getResourceId())
                        || !fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new ArenaProblem.Conflict(
                            "A chave de idempotência já foi utilizada com dados diferentes.");
                }
                return catalog.eventResponse(eventId);
            }
        }

        EventResponse response;
        if (alreadyApplied(event, request)) {
            response = catalog.eventResponse(eventId);
        } else {
            response = catalog.recordResult(eventId, request);
        }

        if (key != null) {
            operationKeys.save(new AdminOperationKey(
                    OPERATION, key, eventId.toString(), fingerprint, Instant.now()));
        }
        return response;
    }

    @Transactional
    public EventResponse recordClassification(Long eventId, EventClassificationRequest request, String headerKey) {
        ArenaEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        String key = normalizedKey(headerKey);
        String fingerprint = classificationFingerprint(request);

        if (key != null) {
            AdminOperationKey existing = operationKeys
                    .findByOperationAndIdempotencyKey(CLASSIFICATION_OPERATION, key)
                    .orElse(null);
            if (existing != null) {
                if (!eventId.toString().equals(existing.getResourceId())
                        || !fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new ArenaProblem.Conflict(
                            "A chave de idempotência já foi utilizada com dados diferentes.");
                }
                return catalog.eventResponse(eventId);
            }
        }

        EventResponse current = catalog.eventResponse(eventId);
        EventResponse response = classificationAlreadyApplied(current, request)
                ? current
                : catalog.recordClassification(eventId, request);

        if (key != null) {
            operationKeys.save(new AdminOperationKey(
                    CLASSIFICATION_OPERATION, key, eventId.toString(), fingerprint, Instant.now()));
        }
        return response;
    }

    private boolean alreadyApplied(ArenaEvent event, EventResultRequest request) {
        boolean sameScore = Objects.equals(event.getHomeScore(), request.homeScore())
                && Objects.equals(event.getAwayScore(), request.awayScore());
        boolean requestedFinished = Boolean.TRUE.equals(request.finishEvent());
        return sameScore && (!requestedFinished || event.getStatus() == EventStatus.FINISHED);
    }

    private String normalizedKey(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        if (key.length() > MAX_KEY_LENGTH || key.chars().anyMatch(Character::isISOControl)) {
            throw new ArenaProblem.RuleViolation(
                    "A chave de idempotência deve ter no máximo 80 caracteres válidos.");
        }
        return key;
    }

    private String fingerprint(EventResultRequest request) {
        return request.homeScore() + ":" + request.awayScore() + ":" + Boolean.TRUE.equals(request.finishEvent());
    }

    private boolean classificationAlreadyApplied(EventResponse current, EventClassificationRequest request) {
        if (Boolean.TRUE.equals(request.finishEvent()) && current.status() != EventStatus.FINISHED) return false;
        if (current.participants().size() != request.participants().size()) return false;

        Map<Long, EventParticipantResponse> persisted = current.participants().stream()
                .collect(Collectors.toMap(value -> value.competitor().id(), Function.identity()));
        return request.participants().stream().allMatch(input -> {
            var value = persisted.get(input.competitorId());
            return value != null
                    && Objects.equals(value.position(), input.position())
                    && normalizedScore(value.scoreLabel()).equals(normalizedScore(input.scoreLabel()));
        });
    }

    private String classificationFingerprint(EventClassificationRequest request) {
        String canonical = request.participants().stream()
                .sorted(Comparator.comparing(EventParticipantRequest::competitorId))
                .map(input -> input.competitorId() + ":" + input.position() + ":" + normalizedScore(input.scoreLabel()))
                .collect(Collectors.joining("|"))
                + ":finish=" + Boolean.TRUE.equals(request.finishEvent());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível para idempotência administrativa.", error);
        }
    }

    private String normalizedScore(String value) {
        return value == null ? "" : value.trim();
    }
}

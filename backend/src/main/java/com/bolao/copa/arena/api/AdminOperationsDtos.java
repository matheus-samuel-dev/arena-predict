package com.bolao.copa.arena.api;

import com.bolao.copa.arena.domain.ArenaEnums.PoolStatus;
import com.bolao.copa.arena.domain.ArenaEnums.PoolType;
import com.bolao.copa.arena.domain.ArenaEnums.ReportStatus;
import com.bolao.copa.arena.api.ArenaDtos.CompetitorSummary;
import com.bolao.copa.arena.api.ArenaDtos.EventParticipantResponse;
import com.bolao.copa.arena.api.ArenaDtos.MarketOptionResponse;
import com.bolao.copa.entity.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Read-only projections used by the administrative resource browser.
 *
 * <p>These records deliberately avoid persistence entities and sensitive fields such as password hashes,
 * JWT data, idempotency keys and private pool invitation codes.</p>
 */
public final class AdminOperationsDtos {
    private AdminOperationsDtos() { }

    public record AdminUserResponse(Long id, String name, String email, UserRole role, String status,
                                    long pointBalance, Instant createdAt, Instant updatedAt) { }

    public record AdminPoolResponse(Long id, String name, String description, PoolStatus status,
                                    Long ownerId, String ownerName, String visibility,
                                    int participantCount, int maxParticipants, int virtualPrizePoints,
                                    String sportName, String championshipName, PoolType poolType, boolean recurring,
                                    Instant startsAt, Instant endsAt, Instant createdAt) { }

    public record AdminChampionshipResponse(Long id, String name, String slug, String status,
                                            Long sportId, String sportName, String season,
                                            String imageUrl, Instant startsAt, Instant endsAt) { }

    public record AdminEventResponse(Long id, String title, String externalKey, String status,
                                     Long championshipId, String championship, String sport,
                                     CompetitorSummary homeCompetitor, CompetitorSummary awayCompetitor,
                                     String stage, String venue, String broadcast, String imageUrl, String format,
                                     int bestOf, List<EventParticipantResponse> participants,
                                     Instant startsAt, Instant predictionClosesAt,
                                     Integer homeScore, Integer awayScore, boolean featured, boolean demo) { }

    public record AdminMarketResponse(Long id, String name, String code, String status,
                                      Long eventId, String eventTitle, String eventStatus, int minimumPoints,
                                      int optionCount, List<MarketOptionResponse> options,
                                      String resultOptionKey, Instant settledAt) { }

    public record ScoringRuleResponse(String id, String name, String description, String status,
                                      String calculation, String unit, Instant updatedAt) { }

    public record OperationalReportResponse(String id, String name, String description, String status,
                                             long value, String unit, Instant updatedAt) { }

    public record AuditEntryResponse(Long id, String title, String action, String actor, Long actorId,
                                     String actorRole, String status, String resourceType, String resourceId,
                                     String summary, String correlationId, Instant createdAt) { }

    public record PublicSettingResponse(String id, String name, String description, String status,
                                        String value, String category, Instant updatedAt) { }

    public record ModerationQueueResponse(Long id, String title, Long postId, String postExcerpt,
                                          Long reporterId, String reporterName, String reason,
                                          ReportStatus status, String moderatorNote,
                                          Instant createdAt, Instant reviewedAt) { }

    /** Stable JSON page contract; avoids exposing Spring Data's internal PageImpl representation. */
    public record AdminPageResponse<T>(List<T> content, long totalElements, int totalPages,
                                       int size, int number, boolean first, boolean last) {
        public static <T> AdminPageResponse<T> from(Page<T> page) {
            return new AdminPageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                    page.getSize(), page.getNumber(), page.isFirst(), page.isLast());
        }
    }
}
